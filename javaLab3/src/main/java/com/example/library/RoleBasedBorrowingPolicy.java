package com.example.library;

public record RoleBasedBorrowingPolicy(int studentLimit, int regularLimit, int premiumLimit)
        implements BorrowingPolicy {
    @Override
    public BorrowingDecision evaluate(BorrowingContext context) {
        int limit = switch (context.member().role()) {
            case STUDENT -> studentLimit;
            case REGULAR -> regularLimit;
            case PREMIUM -> premiumLimit;
        };
        return new FixedBorrowingPolicy(limit).evaluate(context);
    }
}
