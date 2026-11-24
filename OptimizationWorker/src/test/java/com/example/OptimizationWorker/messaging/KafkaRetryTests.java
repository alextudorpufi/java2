package com.example.OptimizationWorker.messaging;

import com.example.OptimizationWorker.messaging.OptimizationMessages.*;
import com.example.OptimizationWorker.solver.GreedyVrpSolver;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.optimization.retry-delay-ms=20")
@DirtiesContext
@EmbeddedKafka(partitions = 3, topics = {KafkaRetryTests.REQUESTS, KafkaRetryTests.RESULTS, KafkaRetryTests.DEAD_LETTERS})
class KafkaRetryTests {
    static final String REQUESTS = "vrp.optimization.requests.v1";
    static final String RESULTS = "vrp.optimization.acknowledgments.v1";
    static final String DEAD_LETTERS = REQUESTS + ".DLT";
    @Autowired KafkaTemplate<String, String> producer;
    @Autowired EmbeddedKafkaBroker broker;
    @MockitoSpyBean GreedyVrpSolver solver;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void retriesTransientFailureAndDeadLettersExhaustedRecord() throws Exception {
        var recovered = request();
        var exhausted = request();
        var following = request();
        doThrow(new IllegalStateException("temporary failure")).doCallRealMethod().when(solver).solve(recovered);
        doThrow(new IllegalStateException("permanent failure")).when(solver).solve(exhausted);

        var config = KafkaTestUtils.consumerProps("retry-observer", "false", broker);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var consumers = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer());
        try (var observer = consumers.createConsumer()) {
            broker.consumeFromEmbeddedTopics(observer, RESULTS, DEAD_LETTERS);
            send(recovered);
            var success = KafkaTestUtils.getSingleRecord(observer, RESULTS, Duration.ofSeconds(15));
            assertEquals(Status.COMPLETED, json.readValue(success.value(), Acknowledgment.class).status());
            assertEquals(recovered.requestId().toString(), success.key());
            verify(solver, times(2)).solve(recovered);

            send(exhausted);
            var deadLetter = KafkaTestUtils.getSingleRecord(observer, DEAD_LETTERS, Duration.ofSeconds(15));
            assertEquals(exhausted.requestId().toString(), deadLetter.key());
            assertEquals(exhausted, json.readValue(deadLetter.value(), Request.class));
            assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC));
            assertNotNull(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE));
            verify(solver, times(3)).solve(exhausted);

            // All three requests use partition zero to verify that this partition keeps moving.
            send(following);
            var next = KafkaTestUtils.getSingleRecord(observer, RESULTS, Duration.ofSeconds(15));
            assertEquals(following.requestId().toString(), next.key());
        }
    }

    private Request request() {
        return new Request(1, UUID.randomUUID(), 1, List.of(), List.of());
    }

    private void send(Request request) throws Exception {
        producer.send(REQUESTS, 0, request.requestId().toString(), json.writeValueAsString(request))
                .get(10, TimeUnit.SECONDS);
    }
}
