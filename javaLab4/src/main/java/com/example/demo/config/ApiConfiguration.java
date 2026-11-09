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
    public OpenAPI routingOpenApi() {
        return new OpenAPI().info(new Info().title("Vehicle Routing API").version("1.0")
                .description("CRUD for customers, vehicles, deliveries and scenarios. "
                        + "GET supports ETag / If-None-Match and JSON/XML via Accept. "
                        + "Canonical routes use /api/v1; unversioned routes remain compatibility aliases. "
                        + "Customers include links to self, collection and deliveries. "
                        + "Scenario membership is shared; referenced resources cannot be deleted."));
    }
}
