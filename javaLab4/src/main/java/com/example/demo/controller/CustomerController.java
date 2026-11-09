package com.example.demo.controller;

import com.example.demo.dto.CustomerRequest;
import com.example.demo.dto.CustomerResponse;
import com.example.demo.dto.DeliveryResponse;
import com.example.demo.service.CustomerService;
import com.example.demo.entity.Customer;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping(value = {"/api/v1/customers", "/api/customers"}, produces = {"application/json", "application/xml"})
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customers;

    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = toResponse(customers.save(request.toEntity()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<CustomerResponse> findAll() {
        return customers.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable Long id) {
        return toResponse(customers.findById(id));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return toResponse(customers.update(id, request.toEntity()));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Discover a customer's deliveries",
            description = "Follow the deliveries link in a customer response. Returns only this customer's deliveries.")
    @GetMapping("/{id}/deliveries")
    public List<DeliveryResponse> deliveries(@PathVariable Long id) {
        return customers.deliveries(id);
    }

    private CustomerResponse toResponse(Customer customer) {
        String collectionUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/customers").toUriString();
        return CustomerResponse.from(customer, collectionUrl);
    }

    @ApiResponse(responseCode = "204", description = "Deleted; no response body")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customers.delete(id);
        return ResponseEntity.noContent().build();
    }
}
