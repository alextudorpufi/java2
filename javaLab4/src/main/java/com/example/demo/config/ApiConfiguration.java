package com.example.demo.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;

@Configuration
public class ApiConfiguration {
    @Bean
    public FilterRegistrationBean<LegacyApiFilter> legacyApiLinks() {
        var registration = new FilterRegistrationBean<>(new LegacyApiFilter());
        registration.addUrlPatterns("/api/*");
        return registration;
    }

    @Bean
    public GroupedOpenApi versionOneApi() {
        return GroupedOpenApi.builder().group("v1").pathsToMatch("/api/v1/**").build();
    }

    @Bean
    public GroupedOpenApi legacyApi() {
        return GroupedOpenApi.builder().group("legacy").pathsToMatch("/api/customers/**",
                "/api/vehicles/**", "/api/deliveries/**", "/api/scenarios/**").build();
    }
    @Bean
    public FilterRegistrationBean<ApiEtagFilter> apiEtags() {
        var filter = new ApiEtagFilter();
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        return registration;
    }

    @Bean
    public OpenAPI routingOpenApi(org.springframework.core.env.Environment environment,
            @org.springframework.beans.factory.annotation.Value("${app.oidc.issuer:}") String issuer) {
        var api = new OpenAPI();
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("keycloak"))) {
            api.components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("keycloak",
                    new io.swagger.v3.oas.models.security.SecurityScheme()
                            .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
                            .description("Keycloak OIDC Authorization Code with PKCE")
                            .flows(new io.swagger.v3.oas.models.security.OAuthFlows().authorizationCode(
                                    new io.swagger.v3.oas.models.security.OAuthFlow()
                                            .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                                            .tokenUrl(issuer + "/protocol/openid-connect/token")
                                            .refreshUrl(issuer + "/protocol/openid-connect/token")
                                            .scopes(new io.swagger.v3.oas.models.security.Scopes()
                                                    .addString("openid", "OpenID Connect login")
                                                    .addString("profile", "Basic profile")
                                                    .addString("email", "Email address"))))))
                    .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement()
                            .addList("keycloak", java.util.List.of("openid", "profile", "email")));
        } else {
            api.components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearerAuth",
                    new io.swagger.v3.oas.models.security.SecurityScheme()
                            .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                            .scheme("bearer").bearerFormat("JWT")))
                    .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth"));
        }
        return api
                .info(new Info().title("Vehicle Routing API").version("1.0")
                .description("CRUD for customers, vehicles, deliveries and scenarios. "
                        + "GET supports ETag / If-None-Match and JSON/XML via Accept. "
                        + "Canonical routes use /api/v1; unversioned routes remain compatibility aliases. "
                        + "Customers include links to self, collection and deliveries. "
                        + "Scenario membership is shared; referenced resources cannot be deleted."));
    }
}
