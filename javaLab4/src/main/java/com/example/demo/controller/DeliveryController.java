package com.example.demo.controller;

import com.example.demo.dto.DeliveryRequest;
import com.example.demo.dto.DeliveryResponse;
import com.example.demo.service.DeliveryService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;

@RestController
@RequestMapping(value = {"/api/v1/deliveries", "/api/deliveries"}, produces = {"application/json", "application/xml"})
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService service;

    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping(consumes = {"application/json", "application/xml"})
    public ResponseEntity<DeliveryResponse> create(@Valid @RequestBody DeliveryRequest request) {
        var created = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<DeliveryResponse> findAll() {
        return service.listResponses();
    }

    @GetMapping("/{id}")
    public DeliveryResponse findById(@PathVariable Long id) {
        return service.response(id);
    }

    @PutMapping(value = "/{id}", consumes = {"application/json", "application/xml"})
    public DeliveryResponse update(@PathVariable Long id, @Valid @RequestBody DeliveryRequest request) {
        return service.update(id, request);
    }

    @ApiResponse(responseCode = "204", description = "Deleted; no response body")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
