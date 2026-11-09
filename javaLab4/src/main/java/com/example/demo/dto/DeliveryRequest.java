package com.example.demo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DeliveryRequest(
        @NotNull @Positive Long customerId,
        @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity,
        String notes,
        @NotNull OffsetDateTime timestamp) {
}
