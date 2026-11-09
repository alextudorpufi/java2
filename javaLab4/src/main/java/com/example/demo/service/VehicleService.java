package com.example.demo.service;

import com.example.demo.entity.Vehicle;
import com.example.demo.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.example.demo.repository.DriverRepository;
import com.example.demo.repository.DepotRepository;
import com.example.demo.dto.VehicleRequest;
import com.example.demo.dto.VehicleResponse;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.exception.ApiConflictException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleService {
    private final VehicleRepository repository;
    private final DriverRepository driverRepository;
    private final DepotRepository depotRepository;

    @Transactional
    public Vehicle save(Vehicle entity) {
        return repository.save(entity);
    }

    public Vehicle findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
    }

    public List<Vehicle> findAll() {
        return repository.findAll();
    }

    public List<VehicleResponse> listResponses() {
        return findAll().stream().map(VehicleResponse::from).toList();
    }

    public VehicleResponse response(Long id) {
        return VehicleResponse.from(findById(id));
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        apply(vehicle, request);
        return VehicleResponse.from(repository.saveAndFlush(vehicle));
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = findById(id);
        if (vehicle.getDriver() != null) {
            throw new ApiConflictException("Unassign the vehicle's driver before editing through this API.");
        }
        apply(vehicle, request);
        return VehicleResponse.from(repository.saveAndFlush(vehicle));
    }

    private void apply(Vehicle vehicle, VehicleRequest request) {
        var depot = depotRepository.findById(request.depotId())
                .orElseThrow(() -> new ResourceNotFoundException("Depot", request.depotId()));
        vehicle.setName(request.name());
        vehicle.setCapacity(request.capacity());
        vehicle.setAvailable(request.available());
        vehicle.setDepot(depot);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findById(id));
        repository.flush();
    }

    public List<Vehicle> findAvailableByDepot(Long depotId) {
        return repository.findByDepotIdAndAvailableTrue(depotId);
    }

    @Transactional
    public void changeAvailability(Long id, boolean available) {
        if (repository.updateAvailability(id, available) == 0) {
            throw new IllegalArgumentException("Vehicle not found: " + id);
        }
    }

    @Transactional
    public Vehicle assignDriver(Long vehicleId, Long driverId) {
        Vehicle vehicle = findById(vehicleId);
        var driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));
        if (vehicle.getDriver() != null && !vehicle.getDriver().getId().equals(driverId)) {
            throw new IllegalStateException("Vehicle already has a driver; unassign it first");
        }
        vehicle.setDriver(driver);
        // The managed entity is updated at transaction commit (dirty checking).
        return vehicle;
    }

    @Transactional
    public void unassignDriver(Long vehicleId) {
        findById(vehicleId).setDriver(null);
    }
}
