package com.example.library;

// Published after each successful borrow whose active count is strictly above the threshold.
public record BorrowingThresholdExceeded(Member member, Book book, long activeLoans, int threshold) { }
