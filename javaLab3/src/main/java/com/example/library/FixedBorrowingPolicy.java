package com.example.library;

public record FixedBorrowingPolicy(int limit) implements BorrowingPolicy {
    @Override
    public BorrowingDecision evaluate(BorrowingContext context) {
        if (!context.available()) {
            return BorrowingDecision.reject("Book is already borrowed: " + context.book().id());
        }
        if (context.borrowedCount() >= limit) {
            return BorrowingDecision.reject("Borrowing limit reached for " + context.member().name());
        }
        return BorrowingDecision.allow(14);
    }
}
