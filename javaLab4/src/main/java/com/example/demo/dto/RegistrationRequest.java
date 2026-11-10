package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record RegistrationRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{2,49}") String username,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotNull @Valid CustomerRequest customer) {}
