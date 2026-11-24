package com.example.demo.controller;

import com.example.demo.messaging.OptimizationMessages.Acknowledgment;
import com.example.demo.optimization.OptimizationService;
import com.example.demo.optimization.OptimizationService.JobView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.UUID;

@RestController
@RequestMapping(value = {"/api/v1", "/api"}, produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
public class OptimizationController {
    private final OptimizationService service;
    public OptimizationController(OptimizationService service) { this.service = service; }

    @Operation(summary = "Queue a snapshot of a scenario for asynchronous optimization")
    @ApiResponse(responseCode = "202", description = "Durably queued; poll Location for acknowledgment")
    @PostMapping("/scenarios/{id}/optimize")
    public ResponseEntity<JobView> submit(@PathVariable long id) {
        var job = service.submit(id);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/v1/optimizations/{requestId}").buildAndExpand(job.requestId()).toUri();
        return ResponseEntity.accepted().location(location).body(job);
    }

    @GetMapping("/optimizations/{requestId}")
    public JobView find(@PathVariable UUID requestId) { return service.find(requestId); }

    @Operation(summary = "Retrieve a completed optimization solution")
    @ApiResponse(responseCode = "409", description = "The job is queued or failed")
    @GetMapping("/optimizations/{requestId}/solution")
    public Acknowledgment solution(@PathVariable UUID requestId) {
        return service.solution(requestId);
    }
}
