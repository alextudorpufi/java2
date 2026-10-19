package com.example.library;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "library.demo-enabled", havingValue = "true")
public class LibraryDemo implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LibraryDemo.class);
    private final MemberRepository members;
    private final BookRepository books;
    private final BorrowingService borrowing;

    public LibraryDemo(MemberRepository members, BookRepository books, BorrowingService borrowing) {
        this.members = members;
        this.books = books;
        this.borrowing = borrowing;
    }

    @Override
    public void run(String... args) {
        members.save(new Member(1, "Ana", Member.Role.PREMIUM));
        // Six attempts demonstrate the default fixed (3), role-based (5), and Drools (5) limits.
        for (int id = 1; id <= 6; id++) {
            books.save(new Book(id, "Book " + id));
            attempt(1, id);
        }
        borrowing.returnBook(1, 1);
        attempt(1, 1);

        members.save(new Member(2, "Dan", Member.Role.STUDENT));
        attempt(2, 1); // Already borrowed by Ana.
        books.save(new Book(7, "Java Textbook", Book.Type.TEXTBOOK));
        attempt(2, 7); // Drools gives textbooks seven days.
        books.save(new Book(8, "Library Encyclopedia", Book.Type.REFERENCE));
        attempt(2, 8); // Drools disallows reference books.
    }

    private void attempt(long memberId, long bookId) {
        try {
            Loan loan = borrowing.borrowBook(new BorrowingRequest(memberId, bookId));
            log.info("Demo loan: memberId={}, bookId={}, borrowingDays={}, dueAt={}",
                    memberId, bookId,
                    java.time.Duration.between(loan.borrowedAt(), loan.dueAt()).toDays(), loan.dueAt());
        } catch (BorrowingException error) {
            log.info("Demo rejection: {}", error.getMessage());
        }
    }
}
