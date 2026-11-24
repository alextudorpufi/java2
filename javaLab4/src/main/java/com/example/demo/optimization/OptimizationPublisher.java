package com.example.demo.optimization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OptimizationPublisher {
    private final OptimizationJobRepository jobs;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    public OptimizationPublisher(OptimizationJobRepository jobs, KafkaTemplate<String, String> kafka,
            @Value("${app.optimization.request-topic}") String topic) {
        this.jobs = jobs;
        this.kafka = kafka;
        this.topic = topic;
    }
    @Transactional
    public void publish(UUID id) throws Exception {
        var job = jobs.findLocked(id).orElseThrow();
        if (job.published) return;
        kafka.send(topic, id.toString(), job.requestJson).get(15, TimeUnit.SECONDS);
        job.published = true;
    }
}
