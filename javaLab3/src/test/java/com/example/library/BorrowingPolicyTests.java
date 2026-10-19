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
                assertLimit(ctx.getBean(BorrowingPolicy.class), role, 3);
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
            assertLimit(policy, Member.Role.STUDENT, 1);
            assertLimit(policy, Member.Role.REGULAR, 4);
            assertLimit(policy, Member.Role.PREMIUM, 7);
        });
    }

    @Test
    void fixedLimitCanBeConfigured() {
        context.withPropertyValues("library.fixed-limit=8").run(ctx ->
                assertLimit(ctx.getBean(BorrowingPolicy.class), Member.Role.REGULAR, 8));
    }

    @Test
    void invalidConfigurationFailsAtStartup() {
        for (String property : new String[] {"library.policy=unknown", "library.fixed-limit=0",
                "library.student-limit=-1", "library.event-threshold=-1"}) {
            context.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
        }
    }

    private void assertLimit(BorrowingPolicy policy, Member.Role role, int limit) {
        Member member = new Member(1, "Ana", role);
        Book book = new Book(1, "Java");
        assertThat(policy.evaluate(new BorrowingContext(member, book, limit - 1, true)).allowed()).isTrue();
        assertThat(policy.evaluate(new BorrowingContext(member, book, limit, true)).allowed()).isFalse();
        assertThat(policy.evaluate(new BorrowingContext(member, book, 0, false)).allowed()).isFalse();
    }
}
