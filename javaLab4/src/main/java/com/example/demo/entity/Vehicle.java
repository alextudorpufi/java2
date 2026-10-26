package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "vehicles")
@Getter
@NoArgsConstructor
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Setter
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal capacity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Setter
    @Column(nullable = false)
    private boolean available = true;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", unique = true)
    private Driver driver;

    public Vehicle(String name, BigDecimal capacity, Depot depot) {
        this.name = name;
        this.capacity = capacity;
        setDepot(depot);
    }

    public void setDepot(Depot depot) {
        Objects.requireNonNull(depot, "A vehicle must have a depot");
        if (this.depot == depot) return;
        if (this.depot != null) this.depot.detachVehicle(this);
        this.depot = depot;
        depot.attachVehicle(this);
    }

    public void setDriver(Driver driver) {
        if (this.driver == driver) return;
        if (driver != null && driver.getVehicle() != null && driver.getVehicle() != this) {
            throw new IllegalStateException("Driver is already assigned to another vehicle");
        }
        if (this.driver != null) this.driver.attachVehicle(null);
        this.driver = driver;
        if (driver != null) driver.attachVehicle(this);
    }
}
