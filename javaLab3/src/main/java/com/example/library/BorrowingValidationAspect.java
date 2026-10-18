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

    @Around("execution(* com.example.library.BorrowingService.borrowBook(long, long)) || "
            + "execution(* com.example.library.BorrowingService.returnBook(long, long))")
    public Object validate(ProceedingJoinPoint call) throws Throwable {
        long memberId = (long) call.getArgs()[0];
        long bookId = (long) call.getArgs()[1];
        // Keep checks and changes atomic, including concurrent borrows and returns.
        synchronized (loans) {
            Member member = members.findById(memberId)
                    .orElseThrow(() -> new BorrowingException("Member not found: " + memberId));
            books.findById(bookId)
                    .orElseThrow(() -> new BorrowingException("Book not found: " + bookId));
            if (call.getSignature().getName().equals("borrowBook")) {
                if (loans.findByBookId(bookId).isPresent()) {
                    throw new BorrowingException("Book is already borrowed: " + bookId);
                }
                if (loans.countByMemberId(memberId) >= policy.limitFor(member)) {
                    throw new BorrowingException("Borrowing limit reached for " + member.name());
                }
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
