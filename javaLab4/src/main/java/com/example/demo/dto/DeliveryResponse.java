package com.example.demo.dto;

import com.example.demo.entity.Delivery;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DeliveryResponse(Long id, Long customerId, BigDecimal quantity, String notes,
                               OffsetDateTime timestamp) {
    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(delivery.getId(), delivery.getCustomer().getId(),
                delivery.getQuantity(), delivery.getNotes(), delivery.getTimestamp());
    }
}
