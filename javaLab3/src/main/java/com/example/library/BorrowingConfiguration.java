package com.example.library;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BorrowingConfiguration {
    @Bean
    BorrowingPolicy borrowingPolicy(LibraryProperties properties) {
        return switch (properties.policy()) {
            case FIXED -> new FixedBorrowingPolicy(properties.fixedLimit());
            case ROLE_BASED -> new RoleBasedBorrowingPolicy(properties.studentLimit(),
                    properties.regularLimit(), properties.premiumLimit());
        };
    }
}
