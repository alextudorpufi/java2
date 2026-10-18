package com.example.library;

public record FixedBorrowingPolicy(int limit) implements BorrowingPolicy {
    @Override
    public int limitFor(Member member) { return limit; }
}
