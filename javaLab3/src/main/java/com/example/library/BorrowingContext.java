package com.example.library;

import java.util.Objects;

/** Facts supplied to a policy for one borrowing attempt. */
public record BorrowingContext(Member member, Book book, long borrowedCount, boolean available) {
    public BorrowingContext {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(book, "book");
        if (borrowedCount < 0) throw new IllegalArgumentException("Negative loan count");
    }
}
