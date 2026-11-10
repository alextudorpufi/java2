package com.example.demo.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/** Identical resource permissions for database login and Keycloak tokens. */
public final class ApiSecurityRules {
    private ApiSecurityRules() {}

    public static void configure(HttpSecurity http, boolean localLogin) throws Exception {
        http.authorizeHttpRequests(authorize -> {
            if (localLogin) authorize.requestMatchers(HttpMethod.POST, "/login", "/register", "/refresh", "/logout").permitAll();
            authorize.requestMatchers(HttpMethod.GET, "/api/vehicles", "/api/vehicles/**",
                            "/api/v1/vehicles", "/api/v1/vehicles/**",
                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers(HttpMethod.POST, "/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/**").hasRole("ADMIN")
                    .requestMatchers("/api/me").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/deliveries/**", "/api/v1/deliveries/**")
                        .hasAnyRole("ADMIN", "DRIVER")
                    .anyRequest().hasRole("ADMIN");
        });
    }
}
