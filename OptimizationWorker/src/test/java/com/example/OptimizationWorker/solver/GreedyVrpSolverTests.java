package com.example.OptimizationWorker.solver;

import com.example.OptimizationWorker.messaging.OptimizationMessages.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GreedyVrpSolverTests {
    private final GreedyVrpSolver solver = new GreedyVrpSolver();
    private final Point depot = new Point(0, 0);
    private Vehicle vehicle(long id, String capacity) { return new Vehicle(id, new BigDecimal(capacity), depot); }
    private Delivery delivery(long id, String quantity, double longitude) {
        return new Delivery(id, new BigDecimal(quantity), new Point(0, longitude));
    }
    private Request request(List<Vehicle> vehicles, List<Delivery> deliveries) {
        return new Request(1, UUID.randomUUID(), 7, vehicles, deliveries);
    }
    @Test void visitsNearestStopsOnceAndReturnsToDepotWithinCapacity() {
        var request = request(List.of(vehicle(1, "0.3")),
                List.of(delivery(20, "0.2", 2), delivery(10, "0.1", 1)));
        var result = solver.solve(request);
        assertEquals(Status.COMPLETED, result.status());
        assertEquals(request.requestId(), result.requestId());
        assertEquals(List.of(10L, 20L), result.routes().getFirst().deliveryIds());
        assertEquals(0, new BigDecimal("0.3").compareTo(result.routes().getFirst().load()));
        assertEquals(444.78, result.routes().getFirst().distanceKm(), 0.1);
        assertTrue(result.unassignedDeliveryIds().isEmpty());
    }
    @Test void usesMultipleVehiclesWithoutExceedingCapacity() {
        var result = solver.solve(request(List.of(vehicle(1, "5"), vehicle(2, "5")),
                List.of(delivery(10, "5", 1), delivery(20, "5", 2))));
        assertEquals(Status.COMPLETED, result.status());
        assertEquals(2, result.routes().size());
        assertEquals(2, result.routes().stream().flatMap(r -> r.deliveryIds().stream()).distinct().count());
        assertTrue(result.routes().stream().allMatch(r -> r.load().compareTo(new BigDecimal("5")) <= 0));
    }
    @Test void reportsUnassignedDeliveriesAndHandlesEmptyScenarios() {
        var result = solver.solve(request(List.of(vehicle(1, "5")), List.of(delivery(10, "6", 1))));
        assertEquals(Status.FAILED, result.status());
        assertEquals(List.of(10L), result.unassignedDeliveryIds());
        assertEquals(Status.COMPLETED, solver.solve(request(List.of(), List.of())).status());
        assertEquals(Status.FAILED, solver.solve(request(List.of(), List.of(delivery(10, "1", 1)))).status());
    }
    @Test void rejectsBadCoordinatesDuplicateIdsAndNonpositiveDemand() {
        assertThrows(IllegalArgumentException.class, () -> solver.solve(request(List.of(vehicle(1,"1")),
                List.of(delivery(10,"0",1)))));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(request(List.of(vehicle(1,"1")),
                List.of(new Delivery(10, BigDecimal.ONE, new Point(Double.NaN, 0))))));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(request(List.of(vehicle(1,"1")),
                List.of(delivery(10,"1",1), delivery(10,"1",2)))));
    }
}
