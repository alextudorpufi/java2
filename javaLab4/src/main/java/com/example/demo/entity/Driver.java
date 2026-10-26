package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "drivers")
@Getter
@NoArgsConstructor
public class Driver extends Person {
    @OneToOne(mappedBy = "driver", fetch = FetchType.LAZY)
    private Vehicle vehicle;

    public Driver(String name, String email, String phone) {
        super(name, email, phone);
    }

    // Vehicle owns the relationship; callers use Vehicle.setDriver().
    void attachVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
}
