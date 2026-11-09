package com.example.demo.dto;

import com.example.demo.entity.Vehicle;
import java.math.BigDecimal;

public record VehicleResponse(Long id, String name, BigDecimal capacity, Long depotId, boolean available) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getName(), vehicle.getCapacity(),
                vehicle.getDepot().getId(), vehicle.isAvailable());
    }
}
