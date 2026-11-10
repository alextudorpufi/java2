package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.service.AccountService;
import com.example.demo.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

@RestController
@org.springframework.context.annotation.Profile("!keycloak")
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class LoginController {
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService tokens;
    private final AccountService accounts;

    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<RefreshTokenService.TokenPair> login(@Valid @RequestBody   request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.issue(authentication));
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenService.TokenPair> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.refresh(request.refreshToken()));
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        tokens.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegistrationRequest request) {
        return accounts.registerCustomer(request);
    }

    @PostMapping("/api/users/drivers")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerDriver(@Valid @RequestBody DriverRegistrationRequest request) {
        return accounts.registerDriver(request);
    }

    @GetMapping("/api/me")
    public UserResponse me(Principal principal) {
        if (principal == null) throw new BadCredentialsException("Authentication required for identity");
        return accounts.current(principal.getName());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    }
}
