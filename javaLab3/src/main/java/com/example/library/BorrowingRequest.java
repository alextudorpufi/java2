package com.example.library;

/** Create a new request per call. The validation aspect attaches the approved decision. */
public final class BorrowingRequest {
    private final long memberId;
    private final long bookId;
    private BorrowingDecision decision;

    public BorrowingRequest(long memberId, long bookId) {
        this.memberId = memberId;
        this.bookId = bookId;
    }

    public long memberId() { return memberId; }
    public long bookId() { return bookId; }

    void approve(BorrowingDecision decision) {
        if (!decision.allowed()) throw new BorrowingException(decision.rejectionReason());
        this.decision = decision;
    }

    BorrowingDecision approvedDecision() {
        if (decision == null) throw new IllegalStateException("Borrow through the Spring service proxy");
        return decision;
    }
}
