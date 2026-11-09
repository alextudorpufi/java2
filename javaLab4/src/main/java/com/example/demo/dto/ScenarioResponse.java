package com.example.demo.dto;

import com.example.demo.entity.Scenario;
import java.util.List;

public record ScenarioResponse(Long id, String name, List<Long> vehicleIds, List<Long> deliveryIds) {
    public static ScenarioResponse from(Scenario scenario) {
        return new ScenarioResponse(scenario.getId(), scenario.getName(),
                scenario.getVehicles().stream().map(vehicle -> vehicle.getId()).sorted().toList(),
                scenario.getDeliveries().stream().map(delivery -> delivery.getId()).sorted().toList());
    }
}
