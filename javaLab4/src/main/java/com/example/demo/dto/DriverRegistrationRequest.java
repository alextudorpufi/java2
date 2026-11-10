package com.example.demo.dto;

import jakarta.validation.constraints.*;

public record DriverRegistrationRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,49}") String username,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String phone) {}
