package com.example.library;

import com.example.library.drools.DroolsBorrowingPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

// Re-run the full service/aspect/concurrency/event contract against the Drools policy.
@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"library.policy=drools", "library.event-threshold=1"})
class DroolsBorrowingIntegrationTests extends BorrowingIntegrationTests {
    @Autowired BorrowingPolicy policy;

    @Test
    void rulesSetDueDatesAndReferenceRejectionDoesNotCreateALoan() {
        assertThat(policy).isInstanceOf(DroolsBorrowingPolicy.class);
        members.save(new Member(10001, "Premium member", Member.Role.PREMIUM));
        books.save(new Book(10001, "Normal book"));
        books.save(new Book(10002, "Textbook", Book.Type.TEXTBOOK));
        books.save(new Book(10003, "Encyclopedia", Book.Type.REFERENCE));
        Loan normal = service.borrowBook(new BorrowingRequest(10001, 10001));
        Loan textbook = service.borrowBook(new BorrowingRequest(10001, 10002));
        assertThat(Duration.between(normal.borrowedAt(), normal.dueAt()).toDays()).isEqualTo(28);
        assertThat(Duration.between(textbook.borrowedAt(), textbook.dueAt()).toDays()).isEqualTo(7);
        assertThatThrownBy(() -> service.borrowBook(new BorrowingRequest(10001, 10003)))
                .isInstanceOf(BorrowingException.class).hasMessageContaining("Reference books");
        assertThat(loans.findByBookId(10003)).isEmpty();
        assertThat(loans.countByMemberId(10001)).isEqualTo(2);
    }
}
