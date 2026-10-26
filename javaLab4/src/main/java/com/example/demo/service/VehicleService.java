package com.example.demo.service;

import com.example.demo.entity.Vehicle;
import com.example.demo.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.example.demo.repository.DriverRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleService {
    private final VehicleRepository repository;
    private final DriverRepository driverRepository;

    @Transactional
    public Vehicle save(Vehicle entity) {
        return repository.save(entity);
    }

    public Vehicle findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + id));
    }

    public List<Vehicle> findAll() {
        return repository.findAll();
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
