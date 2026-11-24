package com.example.OptimizationWorker.messaging;

import com.example.OptimizationWorker.messaging.OptimizationMessages.*;
import com.example.OptimizationWorker.solver.GreedyVrpSolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OptimizationListener {
    private final JsonMapper json = JsonMapper.builder().build();
    private final GreedyVrpSolver solver;
    private final KafkaTemplate<String, String> kafka;
    private final String acknowledgmentTopic;

    public OptimizationListener(GreedyVrpSolver solver, KafkaTemplate<String, String> kafka,
            @Value("${app.optimization.ack-topic}") String topic) {
        this.solver = solver;
        this.kafka = kafka;
        this.acknowledgmentTopic = topic;
    }

    @KafkaListener(topics = "${app.optimization.request-topic}")
    public void consume(String payload) throws Exception {
        Request request = json.readValue(payload, Request.class);
        Acknowledgment acknowledgment;
        try {
            acknowledgment = solver.solve(request);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            if (request.requestId() == null) throw invalid;
            acknowledgment = new Acknowledgment(1, request.requestId(), request.scenarioId(),
                    Status.FAILED, List.of(), List.of(), "Invalid request: " + invalid.getMessage());
        }
        // Return only after Kafka confirms publication: failures must retry the input record.
        kafka.send(acknowledgmentTopic, request.requestId().toString(), json.writeValueAsString(acknowledgment))
                .get(15, TimeUnit.SECONDS);
    }
}
