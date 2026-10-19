package com.example.library;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class BorrowingService {
    private final MemberRepository members;
    private final BookRepository books;
    private final LoanRepository loans;
    private final LibraryProperties properties;
    private final ApplicationEventPublisher events;

    public BorrowingService(MemberRepository members, BookRepository books, LoanRepository loans,
                            LibraryProperties properties,
                            ApplicationEventPublisher events) {
        this.members = members;
        this.books = books;
        this.loans = loans;
        this.properties = properties;
        this.events = events;
    }

    // Call through the Spring bean so the validation and logging aspects execute.
    public Loan borrowBook(BorrowingRequest request) {
        long memberId = request.memberId();
        long bookId = request.bookId();
        BorrowingDecision decision = request.approvedDecision();
        Member member = members.findById(memberId).orElseThrow();
        Book book = books.findById(bookId).orElseThrow();
        Instant now = Instant.now();
        Loan loan = new Loan(memberId, bookId, now, now.plus(decision.borrowingDays(), ChronoUnit.DAYS));
        loans.add(loan);
        long count = loans.countByMemberId(memberId);
        if (count > properties.eventThreshold()) {
            events.publishEvent(new BorrowingThresholdExceeded(member, book, count, properties.eventThreshold()));
        }
        return loan;
    }

    public void returnBook(long memberId, long bookId) {
        loans.remove(bookId);
    }

}
