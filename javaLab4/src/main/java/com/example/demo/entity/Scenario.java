package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "scenarios")
@Getter
@NoArgsConstructor
public class Scenario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    // No cascade: vehicles and deliveries may be shared by multiple scenarios.
    @ManyToMany
    @JoinTable(name = "scenario_vehicles",
            joinColumns = @JoinColumn(name = "scenario_id"),
            inverseJoinColumns = @JoinColumn(name = "vehicle_id"))
    private Set<Vehicle> vehicles = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "scenario_deliveries",
            joinColumns = @JoinColumn(name = "scenario_id"),
            inverseJoinColumns = @JoinColumn(name = "delivery_id"))
    private Set<Delivery> deliveries = new LinkedHashSet<>();

    public Set<Vehicle> getVehicles() {
        return Collections.unmodifiableSet(vehicles);
    }

    public Set<Delivery> getDeliveries() {
        return Collections.unmodifiableSet(deliveries);
    }

    public void replaceMembership(Set<Vehicle> vehicles, Set<Delivery> deliveries) {
        this.vehicles.clear();
        this.vehicles.addAll(vehicles);
        this.deliveries.clear();
        this.deliveries.addAll(deliveries);
    }
}
