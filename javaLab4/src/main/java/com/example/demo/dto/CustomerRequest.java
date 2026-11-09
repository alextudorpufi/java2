package com.example.demo.dto;

import com.example.demo.entity.Customer;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CustomerRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 500) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90")
        @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180")
        @Digits(integer = 3, fraction = 6) BigDecimal longitude) {

    public Customer toEntity() {
        return new Customer(name, email, phone, address, latitude, longitude);
    }
}
