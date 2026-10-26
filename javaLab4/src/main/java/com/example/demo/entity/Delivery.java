package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor
public class Delivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Setter
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Setter
    @Column(columnDefinition = "text")
    private String notes;

    @Setter
    @Column(nullable = false)
    private OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);

    public Delivery(Customer customer, BigDecimal quantity, String notes) {
        setCustomer(customer);
        this.quantity = quantity;
        this.notes = notes;
    }

    public void setCustomer(Customer customer) {
        Objects.requireNonNull(customer, "A delivery must have a customer");
        if (this.customer == customer) return;
        if (this.customer != null) this.customer.detachDelivery(this);
        this.customer = customer;
        customer.attachDelivery(this);
    }
}
