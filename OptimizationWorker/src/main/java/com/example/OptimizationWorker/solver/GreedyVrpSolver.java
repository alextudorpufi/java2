package com.example.OptimizationWorker.solver;

import com.example.OptimizationWorker.messaging.OptimizationMessages.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class GreedyVrpSolver {
    public Acknowledgment solve(Request request) {
        if (request.version() != 1) throw new IllegalArgumentException("Unsupported contract version");
        Objects.requireNonNull(request.requestId(), "requestId");
        var vehicles = new ArrayList<>(Objects.requireNonNull(request.vehicles(), "vehicles"));
        var remaining = new ArrayList<>(Objects.requireNonNull(request.deliveries(), "deliveries"));
        Set<Long> ids = new HashSet<>();
        for (Vehicle v : vehicles) {
            if (v.capacity() == null || v.capacity().signum() <= 0 || !ids.add(v.id()))
                throw new IllegalArgumentException("Vehicle capacities must be positive and IDs unique");
            validate(v.depot());
        }
        ids.clear();
        for (Delivery d : remaining) {
            if (d.quantity() == null || d.quantity().signum() <= 0 || !ids.add(d.id()))
                throw new IllegalArgumentException("Delivery quantities must be positive and IDs unique");
            validate(d.destination());
        }
        vehicles.sort(Comparator.comparing(Vehicle::capacity).reversed().thenComparingLong(Vehicle::id));
        remaining.sort(Comparator.comparingLong(Delivery::id));
        List<Route> routes = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            BigDecimal load = BigDecimal.ZERO;
            Point current = vehicle.depot();
            double distance = 0;
            List<Long> stops = new ArrayList<>();
            while (true) {
                Delivery next = null;
                double nearest = Double.POSITIVE_INFINITY;
                for (Delivery delivery : remaining) {
                    double km = distance(current, delivery.destination());
                    if (load.add(delivery.quantity()).compareTo(vehicle.capacity()) <= 0 && km < nearest) {
                        next = delivery;
                        nearest = km;
                    }
                }
                if (next == null) break;
                stops.add(next.id());
                load = load.add(next.quantity());
                distance += nearest;
                current = next.destination();
                remaining.remove(next);
            }
            if (!stops.isEmpty()) routes.add(new Route(vehicle.id(), List.copyOf(stops), load,
                    distance + distance(current, vehicle.depot())));
        }
        boolean complete = remaining.isEmpty();
        return new Acknowledgment(1, request.requestId(), request.scenarioId(),
                complete ? Status.COMPLETED : Status.FAILED, List.copyOf(routes),
                remaining.stream().map(Delivery::id).toList(), complete
                ? "Capacity-aware nearest-neighbor solution; optimality is not guaranteed"
                : "Heuristic could not assign all deliveries; this does not prove infeasibility");
    }

    private void validate(Point p) {
        if (p == null || !Double.isFinite(p.latitude()) || !Double.isFinite(p.longitude())
                || Math.abs(p.latitude()) > 90 || Math.abs(p.longitude()) > 180)
            throw new IllegalArgumentException("Invalid coordinates");
    }

    private double distance(Point a, Point b) {
        double lat = Math.toRadians(b.latitude() - a.latitude());
        double lon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(lat / 2), 2) + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude())) * Math.pow(Math.sin(lon / 2), 2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
    }
}
