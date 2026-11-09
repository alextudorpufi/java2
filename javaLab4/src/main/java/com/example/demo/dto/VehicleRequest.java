package com.example.demo.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record VehicleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal capacity,
        @NotNull @Positive Long depotId,
        @NotNull Boolean available) {
}
