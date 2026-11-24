package com.example.demo.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.optimization.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class OptimizationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OptimizationDispatcher.class);
    private final OptimizationJobRepository jobs;
    private final OptimizationPublisher publisher;
    public OptimizationDispatcher(OptimizationJobRepository jobs, OptimizationPublisher publisher) {
        this.jobs = jobs;
        this.publisher = publisher;
    }
    @Scheduled(fixedDelayString = "${app.optimization.dispatch-delay-ms:2000}")
    public void dispatch() {
        for (var job : jobs.findTop20ByPublishedFalseOrderByIdAsc()) {
            try {
                publisher.publish(job.id);
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Publication of optimization {} failed; will retry: {}", job.id, failure.toString());
                break;
            }
        }
    }
}
