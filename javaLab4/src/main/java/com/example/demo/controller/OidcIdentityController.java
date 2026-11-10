package com.example.demo.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@Profile("keycloak")
public class OidcIdentityController {
    @GetMapping("/api/me")
    public Identity me(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken bearer)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var jwt = bearer.getToken();
        return new Identity(jwt.getSubject(), jwt.getClaimAsString("preferred_username"), jwt.getIssuer().toString(),
                bearer.getAuthorities().stream().map(role -> role.getAuthority().substring("ROLE_".length())).sorted().toList());
    }

    public record Identity(String subject, String username, String issuer, List<String> roles) {}
}
