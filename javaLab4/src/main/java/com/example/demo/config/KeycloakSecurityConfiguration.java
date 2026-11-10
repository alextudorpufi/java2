package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("keycloak & (!dev | prod)")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KeycloakSecurityConfiguration {
    @Bean
    public JwtDecoder keycloakJwtDecoder(@Value("${app.oidc.issuer}") String issuer,
                                        @Value("${app.oidc.jwk-set-uri}") String jwks,
                                        @Value("${app.oidc.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(validator(issuer, audience));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> accessToken = jwt -> {
            if (jwt.getAudience() != null && jwt.getAudience().contains(audience) && jwt.getExpiresAt() != null
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && "Bearer".equals(jwt.getClaimAsString("typ"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid API access token", null));
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), accessToken);
    }

    @Bean
    public SecurityFilterChain keycloakSecurityFilterChain(HttpSecurity http, JwtDecoder decoder,
                                                          @Value("${app.oidc.audience}") String audience) throws Exception {
        ApiSecurityRules.configure(http, false);
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder)
                        .jwtAuthenticationConverter(new KeycloakRoleConverter(audience))))
                .build();
    }
}
