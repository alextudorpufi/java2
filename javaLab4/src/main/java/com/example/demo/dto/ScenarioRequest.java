package com.example.demo.dto;

import jakarta.validation.constraints.*;
import java.util.Set;

public record ScenarioRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Set<@NotNull @Positive Long> vehicleIds,
        @NotNull Set<@NotNull @Positive Long> deliveryIds) {
}
