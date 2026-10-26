package com.example.demo.search;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Immutable query result: safe to cache, with no lazy entity relationships. */
public record DeliveryView(Long id, Long customerId, String customerName,
                           BigDecimal quantity, OffsetDateTime timestamp) {
}
