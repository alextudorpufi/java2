package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@org.springframework.context.annotation.Profile("!keycloak")
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttl;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.issuer}") String issuer,
                        @Value("${app.jwt.ttl-seconds}") long ttl) {
        if (ttl <= 0 || ttl > 3600) throw new IllegalArgumentException("JWT TTL must be 1..3600 seconds");
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public TokenResponse issue(Authentication authentication) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(authentication.getName())
                .id(java.util.UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(ttl))
                .claim("roles", authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", "")).toList()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl);
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
