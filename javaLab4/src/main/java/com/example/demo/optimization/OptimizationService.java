package com.example.demo.optimization;

import com.example.demo.messaging.OptimizationMessages.*;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ScenarioRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Service
public class OptimizationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OptimizationService.class);
    private final ScenarioRepository scenarios;
    private final OptimizationJobRepository jobs;
    private final JsonMapper json = JsonMapper.builder().build();
    public OptimizationService(ScenarioRepository scenarios, OptimizationJobRepository jobs) {
        this.scenarios = scenarios;
        this.jobs = jobs;
    }
    public record JobView(UUID requestId, long scenarioId, String status, Acknowledgment acknowledgment) {}

    @Transactional
    public JobView submit(long scenarioId) {
        var scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Scenario", scenarioId));
        UUID id = UUID.randomUUID();
        var vehicles = scenario.getVehicles().stream().filter(v -> v.isAvailable())
                .sorted(Comparator.comparing(v -> v.getId()))
                .map(v -> new Vehicle(v.getId(), v.getCapacity(), new Point(
                        v.getDepot().getLatitude().doubleValue(), v.getDepot().getLongitude().doubleValue())))
                .toList();
        var deliveries = scenario.getDeliveries().stream().sorted(Comparator.comparing(d -> d.getId()))
                .map(d -> new Delivery(d.getId(), d.getQuantity(), new Point(
                        d.getCustomer().getLatitude().doubleValue(), d.getCustomer().getLongitude().doubleValue())))
                .toList();
        Request request = new Request(1, id, scenarioId, vehicles, deliveries);
        jobs.save(new OptimizationJob(id, scenarioId, json.writeValueAsString(request)));
        return new JobView(id, scenarioId, "QUEUED", null);
    }

    @Transactional(readOnly = true)
    public JobView find(UUID id) {
        var job = jobs.findById(id).orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Optimization job not found: " + id));
        return new JobView(job.id, job.scenarioId, job.status, job.acknowledgmentJson == null ? null
                : json.readValue(job.acknowledgmentJson, Acknowledgment.class));
    }

    @Transactional(readOnly = true)
    public Acknowledgment solution(UUID id) {
        var job = find(id);
        if (!"COMPLETED".equals(job.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solution is unavailable while job status is " + job.status());
        }
        return job.acknowledgment();
    }

    @Transactional
    @KafkaListener(topics = "${app.optimization.ack-topic}")
    public void acknowledge(String payload) {
        var ack = json.readValue(payload, Acknowledgment.class);
        if (ack == null || ack.version() != 1 || ack.requestId() == null || ack.status() == null)
            throw new IllegalArgumentException("Invalid acknowledgment");
        var job = jobs.findLocked(ack.requestId()).orElse(null);
        if (job == null) {
            log.warn("Ignoring acknowledgment for unknown request {}", ack.requestId());
            return;
        }
        if (job.scenarioId != ack.scenarioId()) throw new IllegalArgumentException("Scenario mismatch");
        // Replayed requests can yield duplicate acknowledgments; terminal results are immutable.
        if (!job.status.equals("QUEUED")) return;
        job.status = ack.status().name();
        job.acknowledgmentJson = payload;
    }

    @Transactional
    @KafkaListener(topics = {"${app.optimization.request-topic}.DLT", "${app.optimization.ack-topic}.DLT"})
    public void handleDeadLetter(ConsumerRecord<String, String> record) {
        UUID id;
        try {
            id = UUID.fromString(Objects.toString(record.key(), ""));
        } catch (IllegalArgumentException invalidKey) {
            log.warn("Cannot correlate dead letter from {} at offset {}", record.topic(), record.offset());
            return;
        }

        var job = jobs.findLocked(id).orElse(null);
        if (job == null || !"QUEUED".equals(job.status)) return;

        var failure = new Acknowledgment(1, job.id, job.scenarioId, Status.FAILED, List.of(), List.of(),
                "Kafka processing failed. See " + record.topic() + " for details.");
        job.status = failure.status().name();
        job.acknowledgmentJson = json.writeValueAsString(failure);
    }
}
