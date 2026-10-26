package com.example.demo.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Null means no restriction. Area is an inclusive latitude/longitude rectangle. */
public record DeliveryFilter(Long customerId, BigDecimal minLatitude, BigDecimal maxLatitude,
                             BigDecimal minLongitude, BigDecimal maxLongitude,
                             BigDecimal quantityAbove, LocalDate date, ZoneId zone) {
    public DeliveryFilter {
        zone = Objects.requireNonNullElse(zone, ZoneId.of("Europe/Bucharest"));
        if (customerId != null && customerId <= 0) throw new IllegalArgumentException("Invalid customer ID");
        range(minLatitude, maxLatitude, 90, "latitude");
        range(minLongitude, maxLongitude, 180, "longitude");
        if (quantityAbove != null && quantityAbove.signum() < 0) {
            throw new IllegalArgumentException("Quantity threshold must not be negative");
        }
    }

    private static void range(BigDecimal min, BigDecimal max, int limit, String name) {
        for (BigDecimal value : new BigDecimal[]{min, max}) {
            if (value != null && value.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
                throw new IllegalArgumentException("Invalid " + name);
            }
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Minimum " + name + " exceeds maximum");
        }
    }

    public static DeliveryFilter all() {
        return new DeliveryFilter(null, null, null, null, null, null, null, null);
    }
}
