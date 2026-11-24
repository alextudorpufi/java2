package com.example.demo.messaging;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Version 1 JSON wire contract. Keep both applications' copies in sync. */
public final class OptimizationMessages {
    private OptimizationMessages() {}
    public record Point(double latitude, double longitude) {}
    public record Vehicle(long id, BigDecimal capacity, Point depot) {}
    public record Delivery(long id, BigDecimal quantity, Point destination) {}
    public record Request(int version, UUID requestId, long scenarioId,
                          List<Vehicle> vehicles, List<Delivery> deliveries) {}
    public enum Status { COMPLETED, FAILED }
    public record Route(long vehicleId, List<Long> deliveryIds, BigDecimal load, double distanceKm) {}
    public record Acknowledgment(int version, UUID requestId, long scenarioId, Status status,
                                 List<Route> routes, List<Long> unassignedDeliveryIds, String message) {}
}
