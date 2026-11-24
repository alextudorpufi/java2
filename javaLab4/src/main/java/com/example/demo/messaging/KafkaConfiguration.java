package com.example.demo.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean NewTopic requests(@Value("${app.optimization.request-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
    @Bean NewTopic acknowledgments(@Value("${app.optimization.ack-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
    @Bean NewTopic deadLetters(@Value("${app.optimization.ack-topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka,
            @Value("${app.optimization.retry-delay-ms:1000}") long retryDelayMs) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka, (record, failure) ->
                new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryDelayMs, 2));
        // A failed DB write while handling a dead letter must not create a dead letter of a dead letter.
        handler.setBackOffFunction((record, failure) -> record.topic().endsWith(".DLT")
                ? new FixedBackOff(retryDelayMs, FixedBackOff.UNLIMITED_ATTEMPTS) : null);
        return handler;
    }
}
