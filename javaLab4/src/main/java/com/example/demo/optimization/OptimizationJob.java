package com.example.demo.optimization;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "optimization_jobs")
public class OptimizationJob {
    @Id UUID id;
    @Column(nullable = false) long scenarioId;
    @Column(nullable = false, columnDefinition = "text") String requestJson;
    @Column(nullable = false) boolean published;
    @Column(nullable = false, length = 20) String status;
    @Column(columnDefinition = "text") String acknowledgmentJson;
    protected OptimizationJob() {}
    OptimizationJob(UUID id, long scenarioId, String requestJson) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.requestJson = requestJson;
        this.status = "QUEUED";
    }
}
