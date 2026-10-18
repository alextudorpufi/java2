package com.example.library;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

class BorrowingPolicyTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, BorrowingConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LibraryProperties.class)
    static class PropertiesConfiguration { }

    @Test
    void defaultPolicyHasFixedLimitForEveryRole() {
        context.run(ctx -> {
            assertThat(ctx).hasSingleBean(BorrowingPolicy.class);
            assertThat(ctx.getBean(BorrowingPolicy.class)).isInstanceOf(FixedBorrowingPolicy.class);
            for (Member.Role role : Member.Role.values()) {
                assertThat(ctx.getBean(BorrowingPolicy.class).limitFor(new Member(1, "Ana", role))).isEqualTo(3);
            }
        });
    }

    @Test
    void rolePolicyUsesConfiguredLimits() {
        context.withPropertyValues("library.policy=role-based", "library.student-limit=1",
                "library.regular-limit=4", "library.premium-limit=7").run(ctx -> {
            assertThat(ctx).hasSingleBean(BorrowingPolicy.class);
            var policy = ctx.getBean(BorrowingPolicy.class);
            assertThat(policy).isInstanceOf(RoleBasedBorrowingPolicy.class);
            assertThat(policy.limitFor(new Member(1, "Ana", Member.Role.STUDENT))).isEqualTo(1);
            assertThat(policy.limitFor(new Member(2, "Dan", Member.Role.REGULAR))).isEqualTo(4);
            assertThat(policy.limitFor(new Member(3, "Mia", Member.Role.PREMIUM))).isEqualTo(7);
        });
    }

    @Test
    void fixedLimitCanBeConfigured() {
        context.withPropertyValues("library.fixed-limit=8").run(ctx ->
                assertThat(ctx.getBean(BorrowingPolicy.class).limitFor(
                        new Member(1, "Ana", Member.Role.REGULAR))).isEqualTo(8));
    }

    @Test
    void invalidConfigurationFailsAtStartup() {
        for (String property : new String[] {"library.policy=unknown", "library.fixed-limit=0",
                "library.student-limit=-1", "library.event-threshold=-1"}) {
            context.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
        }
    }
}
