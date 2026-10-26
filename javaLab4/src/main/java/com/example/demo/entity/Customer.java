package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor
public class Customer extends Person {
    @Setter
    @Column(nullable = false, length = 500)
    private String address;

    @Setter
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Setter
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @OneToMany(mappedBy = "customer")
    private List<Delivery> deliveries = new ArrayList<>();

    public Customer(String name, String email, String phone, String address,
                    BigDecimal latitude, BigDecimal longitude) {
        super(name, email, phone);
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public List<Delivery> getDeliveries() {
        return Collections.unmodifiableList(deliveries);
    }

    void attachDelivery(Delivery delivery) { deliveries.add(delivery); }
    void detachDelivery(Delivery delivery) { deliveries.remove(delivery); }
}
