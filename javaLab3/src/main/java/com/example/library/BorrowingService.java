package com.example.library;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class BorrowingService {
    private final MemberRepository members;
    private final BookRepository books;
    private final LoanRepository loans;
    private final BorrowingPolicy policy;
    private final LibraryProperties properties;
    private final ApplicationEventPublisher events;

    public BorrowingService(MemberRepository members, BookRepository books, LoanRepository loans,
                            BorrowingPolicy policy, LibraryProperties properties,
                            ApplicationEventPublisher events) {
        this.members = members;
        this.books = books;
        this.loans = loans;
        this.policy = policy;
        this.properties = properties;
        this.events = events;
    }

    // Call through the Spring bean so the validation and logging aspects execute.
    public Loan borrowBook(long memberId, long bookId) {
        Member member = members.findById(memberId).orElseThrow();
        Book book = books.findById(bookId).orElseThrow();
        Loan loan = new Loan(memberId, bookId, Instant.now());
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

    public int borrowingLimit(long memberId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new BorrowingException("Member not found: " + memberId));
        return policy.limitFor(member);
    }
}
