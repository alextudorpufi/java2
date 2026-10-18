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
        int limit = borrowing.borrowingLimit(1);
        for (int id = 1; id <= limit + 1; id++) {
            books.save(new Book(id, "Book " + id));
        }
        for (int id = 1; id <= limit; id++) {
            borrowing.borrowBook(1, id);
        }
        try {
            borrowing.borrowBook(1, limit + 1);
        } catch (BorrowingException error) {
            log.info("Expected demo rejection: {}", error.getMessage());
        }
        borrowing.returnBook(1, 1);
        borrowing.borrowBook(1, limit + 1);
    }
}
