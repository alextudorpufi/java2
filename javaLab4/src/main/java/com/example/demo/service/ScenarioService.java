package com.example.demo.service;

import com.example.demo.dto.ScenarioRequest;
import com.example.demo.dto.ScenarioResponse;
import com.example.demo.entity.*;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScenarioService {
    private final ScenarioRepository repository;
    private final VehicleRepository vehicles;
    private final DeliveryRepository deliveries;

    public List<ScenarioResponse> listResponses() {
        return repository.findAll().stream().map(ScenarioResponse::from).toList();
    }

    public ScenarioResponse response(Long id) {
        return ScenarioResponse.from(findById(id));
    }

    private Scenario findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scenario", id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ScenarioResponse create(ScenarioRequest request) {
        Scenario scenario = new Scenario();
        apply(scenario, request);
        return ScenarioResponse.from(repository.saveAndFlush(scenario));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ScenarioResponse update(Long id, ScenarioRequest request) {
        Scenario scenario = findById(id);
        apply(scenario, request);
        return ScenarioResponse.from(repository.saveAndFlush(scenario));
    }

    private void apply(Scenario scenario, ScenarioRequest request) {
        // Resolve every ID before modifying membership, rejecting partial/missing sets.
        Set<Vehicle> selectedVehicles = resolve(request.vehicleIds(), vehicles.findAllById(request.vehicleIds()),
                Vehicle::getId, "Vehicle");
        Set<Delivery> selectedDeliveries = resolve(request.deliveryIds(), deliveries.findAllById(request.deliveryIds()),
                Delivery::getId, "Delivery");
        scenario.setName(request.name());
        scenario.replaceMembership(selectedVehicles, selectedDeliveries);
    }

    private static <T> Set<T> resolve(Set<Long> requested, List<T> found,
                                      Function<T, Long> id, String resource) {
        Set<Long> missing = new LinkedHashSet<>(requested);
        found.forEach(entity -> missing.remove(id.apply(entity)));
        if (!missing.isEmpty()) throw new ResourceNotFoundException(resource, missing.iterator().next());
        return new LinkedHashSet<>(found);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        // Hibernate removes the owning join rows, while shared entities remain intact.
        repository.delete(findById(id));
        repository.flush();
    }
}
