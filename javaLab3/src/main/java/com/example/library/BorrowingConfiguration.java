package com.example.library;

import com.example.library.drools.DroolsBorrowingPolicy;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BorrowingConfiguration {
    @Bean
    BorrowingPolicy borrowingPolicy(LibraryProperties properties) throws IOException {
        return switch (properties.policy()) {
            case DROOLS -> new DroolsBorrowingPolicy(new ClassPathResource("rules/borrowing.drl"));
            case FIXED -> new FixedBorrowingPolicy(properties.fixedLimit());
            case ROLE_BASED -> new RoleBasedBorrowingPolicy(properties.studentLimit(),
                    properties.regularLimit(), properties.premiumLimit());
        };
    }
}
