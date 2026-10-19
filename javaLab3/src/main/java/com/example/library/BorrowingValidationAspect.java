package com.example.library;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(20)
public class BorrowingValidationAspect {
    private final MemberRepository members;
    private final BookRepository books;
    private final LoanRepository loans;
    private final BorrowingPolicy policy;

    public BorrowingValidationAspect(MemberRepository members, BookRepository books,
                                     LoanRepository loans, BorrowingPolicy policy) {
        this.members = members;
        this.books = books;
        this.loans = loans;
        this.policy = policy;
    }

    @Around("execution(* com.example.library.BorrowingService.borrowBook(com.example.library.BorrowingRequest)) || "
            + "execution(* com.example.library.BorrowingService.returnBook(long, long))")
    public Object validate(ProceedingJoinPoint call) throws Throwable {
        boolean borrowing = call.getSignature().getName().equals("borrowBook");
        BorrowingRequest request = borrowing ? (BorrowingRequest) call.getArgs()[0] : null;
        long memberId = borrowing ? request.memberId() : (long) call.getArgs()[0];
        long bookId = borrowing ? request.bookId() : (long) call.getArgs()[1];
        // Keep checks and changes atomic, including concurrent borrows and returns.
        synchronized (loans) {
            Member member = members.findById(memberId)
                    .orElseThrow(() -> new BorrowingException("Member not found: " + memberId));
            Book book = books.findById(bookId)
                    .orElseThrow(() -> new BorrowingException("Book not found: " + bookId));
            if (borrowing) {
                BorrowingContext context = new BorrowingContext(member, book,
                        loans.countByMemberId(memberId), loans.findByBookId(bookId).isEmpty());
                // Evaluate exactly once, then pass the approved period to the service.
                request.approve(policy.evaluate(context));
            } else {
                Loan loan = loans.findByBookId(bookId)
                        .orElseThrow(() -> new BorrowingException("Book is not borrowed: " + bookId));
                if (loan.memberId() != memberId) {
                    throw new BorrowingException("Book was borrowed by another member: " + bookId);
                }
            }
            return call.proceed();
        }
    }
}
