package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.exception.ApiConflictException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {
    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final DriverRepository drivers;
    private final PasswordEncoder encoder;

    public UserResponse registerCustomer(RegistrationRequest request) {
        var user = newAccount(request.username(), request.password(), Role.CUSTOMER);
        var customer = request.customer().toEntity();
        customer.setUser(user);
        customers.saveAndFlush(customer);
        return UserResponse.from(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse registerDriver(DriverRegistrationRequest request) {
        var user = newAccount(request.username(), request.password(), Role.DRIVER);
        var driver = new Driver(request.name(), request.email(), request.phone());
        driver.setUser(user);
        drivers.saveAndFlush(driver);
        return UserResponse.from(user);
    }

    private AppUser newAccount(String username, String password, Role role) {
        if (users.existsByUsername(username)) throw new ApiConflictException("Username already exists");
        validatePassword(password);
        return new AppUser(username, encoder.encode(password), role, true);
    }

    public static void validatePassword(String password) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse current(String username) {
        return users.findByUsername(username).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
