package com.example.library;

import com.example.library.drools.DroolsBorrowingPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class DroolsBorrowingPolicyTests {
    private static BorrowingPolicy policy;

    @BeforeAll
    static void loadRules() throws Exception {
        policy = new DroolsBorrowingPolicy(new ClassPathResource("rules/borrowing.drl"));
    }

    @ParameterizedTest
    @EnumSource(Member.Role.class)
    void membershipControlsLimitAndNormalBookPeriod(Member.Role role) {
        int limit = switch (role) { case STUDENT -> 2; case REGULAR -> 3; case PREMIUM -> 5; };
        int days = switch (role) { case STUDENT -> 14; case REGULAR -> 21; case PREMIUM -> 28; };
        assertThat(policy.evaluate(context(role, Book.Type.NORMAL, limit - 1, true)))
                .isEqualTo(BorrowingDecision.allow(days));
        assertThat(policy.evaluate(context(role, Book.Type.NORMAL, limit, true)).allowed()).isFalse();
        assertThat(policy.evaluate(context(role, Book.Type.NORMAL, limit + 1, true)).allowed()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Member.Role.class)
    void textbooksHaveShorterPeriods(Member.Role role) {
        assertThat(policy.evaluate(context(role, Book.Type.TEXTBOOK, 0, true)))
                .isEqualTo(BorrowingDecision.allow(7));
    }

    @ParameterizedTest
    @EnumSource(Member.Role.class)
    void referenceBooksAndUnavailableCopiesAreRejectedForEveryRole(Member.Role role) {
        assertThat(policy.evaluate(context(role, Book.Type.REFERENCE, 0, true)).rejectionReason())
                .contains("Reference books");
        BorrowingDecision unavailable = policy.evaluate(context(role, Book.Type.NORMAL, 0, false));
        assertThat(unavailable.allowed()).isFalse();
        assertThat(unavailable.borrowingDays()).isZero();
        assertThat(unavailable.rejectionReason()).contains("already borrowed");
    }

    @Test
    void unavailableBookIsRejectedEvenForPremiumMembersAtTheirLimit() {
        BorrowingDecision decision = policy.evaluate(context(Member.Role.PREMIUM, Book.Type.REFERENCE, 5, false));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.rejectionReason()).contains("already borrowed");
        // A previous rejected request must not contaminate a later session.
        assertThat(policy.evaluate(context(Member.Role.PREMIUM, Book.Type.NORMAL, 0, true)).allowed()).isTrue();
    }

    @Test
    void missingRulesFailClosed() throws Exception {
        BorrowingPolicy emptyPolicy = new DroolsBorrowingPolicy(new ByteArrayResource(
                "package com.example.empty; global java.util.List decisions;".getBytes(StandardCharsets.UTF_8)));
        assertThat(emptyPolicy.evaluate(context(Member.Role.PREMIUM, Book.Type.NORMAL, 0, true)))
                .isEqualTo(BorrowingDecision.reject("Borrowing limit reached or no matching borrowing rule"));
    }

    @Test
    void invalidRulesFailDuringLoading() {
        assertThatThrownBy(() -> new DroolsBorrowingPolicy(new ByteArrayResource(
                "this is not valid DRL".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void changingOnlyTheRuleChangesThePeriod() throws Exception {
        String drl = new ClassPathResource("rules/borrowing.drl").getContentAsString(StandardCharsets.UTF_8);
        BorrowingPolicy changedPolicy = new DroolsBorrowingPolicy(new ByteArrayResource(
                drl.replace("? 7 : 28", "? 7 : 35").getBytes(StandardCharsets.UTF_8)));
        assertThat(changedPolicy.evaluate(context(Member.Role.PREMIUM, Book.Type.NORMAL, 0, true)))
                .isEqualTo(BorrowingDecision.allow(35));
    }

    private BorrowingContext context(Member.Role role, Book.Type type, long count, boolean available) {
        return new BorrowingContext(new Member(1, "Ana", role), new Book(1, "Java", type), count, available);
    }
}
