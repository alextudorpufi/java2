package com.example.demo.config;

import com.example.demo.repository.AppUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("!keycloak & (!dev | prod)")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AppUserRepository users) throws Exception {
        ApiSecurityRules.configure(http, true);
        return http
                // Credentials are only accepted as Bearer headers, never cookies or Basic auth.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    // Reload roles and enabled status so revocation takes effect immediately.
                    var user = users.findByUsername(token.getSubject())
                            .filter(account -> account.isEnabled())
                            .orElseThrow(() -> new OAuth2AuthenticationException("invalid_token"));
                    return new JwtAuthenticationToken(token, user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList(), user.getUsername());
                })))
                .build();
    }
}
