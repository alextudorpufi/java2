package com.example.demo.controller;

import com.example.demo.dto.VehicleRequest;
import com.example.demo.dto.VehicleResponse;
import com.example.demo.service.VehicleService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;

@RestController
@RequestMapping(value = {"/api/v1/vehicles", "/api/vehicles"}, produces = {"application/json", "application/xml"})
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleService service;

    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping(consumes = {"application/json", "application/xml"})
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
        var created = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<VehicleResponse> findAll() {
        return service.listResponses();
    }

    @GetMapping("/{id}")
    public VehicleResponse findById(@PathVariable Long id) {
        return service.response(id);
    }

    @PutMapping(value = "/{id}", consumes = {"application/json", "application/xml"})
    public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return service.update(id, request);
    }

    @ApiResponse(responseCode = "204", description = "Deleted; no response body")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
