package com.example.library;

public interface BorrowingPolicy {
    BorrowingDecision evaluate(BorrowingContext context);
}
