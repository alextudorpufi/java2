package com.example.library;

import java.time.Instant;

public record Loan(long memberId, long bookId, Instant borrowedAt, Instant dueAt) { }
