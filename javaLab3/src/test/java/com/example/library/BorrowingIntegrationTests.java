package com.example.library;

import com.example.library.injection.InjectionOrderDemo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = LibraryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"library.fixed-limit=2", "library.event-threshold=1"})
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class BorrowingIntegrationTests {
    private static final AtomicLong ids = new AtomicLong(100);
    @Autowired BorrowingService service;
    @Autowired MemberRepository members;
    @Autowired BookRepository books;
    @Autowired LoanRepository loans;
    @Autowired InjectionOrderDemo injection;
    @Autowired ApplicationEvents events;
    private long memberId;
    private long firstBook;
    private long secondBook;
    private long thirdBook;

    @BeforeEach
    void seed() {
        memberId = ids.incrementAndGet();
        firstBook = ids.incrementAndGet();
        secondBook = ids.incrementAndGet();
        thirdBook = ids.incrementAndGet();
        members.save(new Member(memberId, "Ana", Member.Role.STUDENT));
        books.save(new Book(firstBook, "Clean Code"));
        books.save(new Book(secondBook, "Effective Java"));
        books.save(new Book(thirdBook, "Design Patterns"));
    }

    @Test
    void injectionOrderIsProvedByDependencyState() {
        assertThat(injection.observations()).hasSize(4);
        assertThat(injection.observations().get(0)).startsWith("constructor:");
        assertThat(injection.observations().get(1)).startsWith("field:");
        assertThat(injection.observations().subList(2, 4))
                .containsExactlyInAnyOrder("setter: field already present", "method: field already present");
    }

    @Test
    void borrowReturnAndThresholdEvent(CapturedOutput output) {
        service.borrowBook(new BorrowingRequest(memberId, firstBook));
        assertThat(events.stream(BorrowingThresholdExceeded.class)).isEmpty();
        service.borrowBook(new BorrowingRequest(memberId, secondBook));
        assertThat(events.stream(BorrowingThresholdExceeded.class)).singleElement().satisfies(event -> {
            assertThat(event.member().id()).isEqualTo(memberId);
            assertThat(event.book().id()).isEqualTo(secondBook);
            assertThat(event.activeLoans()).isEqualTo(2);
        });
        assertThatThrownBy(() -> service.borrowBook(new BorrowingRequest(memberId, thirdBook)))
                .isInstanceOf(BorrowingException.class).hasMessageContaining("limit reached");
        assertThat(loans.countByMemberId(memberId)).isEqualTo(2);
        service.returnBook(memberId, firstBook);
        assertThat(loans.findByBookId(firstBook)).isEmpty();
        service.borrowBook(new BorrowingRequest(memberId, thirdBook));
        assertThat(events.stream(BorrowingThresholdExceeded.class)).hasSize(2);
        assertThat(output).contains("operation=borrowBook", "operation=returnBook", "member=Ana",
                "book=Clean Code", "executionMs=", "Failed operation=borrowBook", "Borrowing event:");
    }

    @Test
    void invalidOperationsLeaveLoansUnchanged(CapturedOutput output) {
        assertThatThrownBy(() -> service.borrowBook(new BorrowingRequest(-1, firstBook)))
                .isInstanceOf(BorrowingException.class).hasMessageContaining("Member not found");
        assertThatThrownBy(() -> service.borrowBook(new BorrowingRequest(memberId, -1)))
                .isInstanceOf(BorrowingException.class).hasMessageContaining("Book not found");
        service.borrowBook(new BorrowingRequest(memberId, firstBook));
        assertThatThrownBy(() -> service.borrowBook(new BorrowingRequest(memberId, firstBook)))
                .isInstanceOf(BorrowingException.class).hasMessageContaining("already borrowed");
        long otherMember = ids.incrementAndGet();
        members.save(new Member(otherMember, "Dan", Member.Role.PREMIUM));
        assertThatThrownBy(() -> service.returnBook(otherMember, firstBook))
                .isInstanceOf(BorrowingException.class).hasMessageContaining("another member");
        assertThatThrownBy(() -> service.returnBook(memberId, secondBook))
                .isInstanceOf(BorrowingException.class).hasMessageContaining("not borrowed");
        assertThat(loans.findByBookId(firstBook)).get().extracting(Loan::memberId).isEqualTo(memberId);
        assertThat(loans.countByMemberId(memberId)).isEqualTo(1);
        assertThat(events.stream(BorrowingThresholdExceeded.class)).isEmpty();
        assertThat(output).contains("Failed operation=returnBook", "unknown member #-1", "unknown book #-1");
    }

    @Test
    void competingBorrowsCannotExceedMemberLimit() throws Exception {
        service.borrowBook(new BorrowingRequest(memberId, firstBook));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> borrowAfter(start, secondBook));
            var two = executor.submit(() -> borrowAfter(start, thirdBook));
            start.countDown();
            assertThat(one.get(5, TimeUnit.SECONDS) + two.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(loans.countByMemberId(memberId)).isEqualTo(2);
    }

    @Test
    void competingMembersCannotBorrowTheSameCopy() throws Exception {
        long otherMember = ids.incrementAndGet();
        members.save(new Member(otherMember, "Dan", Member.Role.REGULAR));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> borrowAfter(start, memberId, firstBook));
            var two = executor.submit(() -> borrowAfter(start, otherMember, firstBook));
            start.countDown();
            assertThat(one.get(5, TimeUnit.SECONDS) + two.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    private int borrowAfter(CountDownLatch start, long bookId) throws InterruptedException {
        return borrowAfter(start, memberId, bookId);
    }

    private int borrowAfter(CountDownLatch start, long borrower, long bookId) throws InterruptedException {
        start.await();
        try {
            service.borrowBook(new BorrowingRequest(borrower, bookId));
            return 1;
        } catch (BorrowingException expected) {
            return 0;
        }
    }
}
