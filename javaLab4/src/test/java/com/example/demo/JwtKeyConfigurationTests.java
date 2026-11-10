package com.example.demo;

import com.example.demo.config.AuthenticationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class JwtKeyConfigurationTests {
    private final AuthenticationConfiguration configuration = new AuthenticationConfiguration();

    @Test
    void keyIsRequiredOutsideDevAndProductionAlwaysWins() {
        for (String[] profiles : new String[][]{{}, {"prod"}, {"dev", "prod"}, {"prod", "dev"}}) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles(profiles);
            assertThrows(IllegalArgumentException.class, () -> configuration.jwtKey("", environment));
            assertThrows(IllegalArgumentException.class, () -> configuration.jwtKey("c2hvcnQ=", environment));
        }
    }

    @Test
    void devCanUseAnEphemeralKeyWithoutConfiguration() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        assertEquals(32, configuration.jwtKey("", environment).getEncoded().length);
    }
}
