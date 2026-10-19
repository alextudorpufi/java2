package com.example.library;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("library")
public record LibraryProperties(
        @DefaultValue("FIXED") Policy policy,
        @DefaultValue("3") int fixedLimit,
        @DefaultValue("2") int studentLimit,
        @DefaultValue("3") int regularLimit,
        @DefaultValue("5") int premiumLimit,
        @DefaultValue("2") int eventThreshold) {
    public enum Policy { FIXED, ROLE_BASED, DROOLS }

    public LibraryProperties {
        if (fixedLimit < 1 || studentLimit < 1 || regularLimit < 1 || premiumLimit < 1) {
            throw new IllegalArgumentException("Borrowing limits must be positive");
        }
        if (eventThreshold < 0) {
            throw new IllegalArgumentException("Event threshold must be nonnegative");
        }
    }
}
