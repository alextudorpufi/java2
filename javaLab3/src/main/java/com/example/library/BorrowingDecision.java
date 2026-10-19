package com.example.library;

/** An immutable policy result. Rejections never carry a borrowing period. */
public record BorrowingDecision(boolean allowed, String rejectionReason, int borrowingDays) {
    public BorrowingDecision {
        if (allowed && (borrowingDays <= 0 || rejectionReason != null)) {
            throw new IllegalArgumentException("Approval needs a positive period and no rejection reason");
        }
        if (!allowed && (borrowingDays != 0 || rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("Rejection needs a reason and no period");
        }
    }

    public static BorrowingDecision allow(int days) { return new BorrowingDecision(true, null, days); }
    public static BorrowingDecision reject(String reason) { return new BorrowingDecision(false, reason, 0); }
}
