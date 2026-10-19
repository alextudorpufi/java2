package com.example.library;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(10)
public class BorrowingLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(BorrowingLoggingAspect.class);
    private final MemberRepository members;
    private final BookRepository books;

    public BorrowingLoggingAspect(MemberRepository members, BookRepository books) {
        this.members = members;
        this.books = books;
    }

    @Pointcut("execution(* com.example.library.BorrowingService.borrowBook(..)) || "
            + "execution(* com.example.library.BorrowingService.returnBook(..))")
    public void borrowingOperation() { }

    @Around("borrowingOperation()")
    public Object logOperation(ProceedingJoinPoint call) throws Throwable {
        long start = System.nanoTime();
        long memberId = memberId(call);
        long bookId = bookId(call);
        try {
            return call.proceed();
        } finally {
            log.info("operation={}, member={}, book={}, executionMs={}",
                    call.getSignature().getName(),
                    members.findById(memberId).map(Member::name).orElse("unknown member #" + memberId),
                    books.findById(bookId).map(Book::title).orElse("unknown book #" + bookId),
                    (System.nanoTime() - start) / 1_000_000.0);
        }
    }

    @AfterThrowing(pointcut = "borrowingOperation()", throwing = "error")
    public void logFailure(JoinPoint call, Exception error) {
        log.error("Failed operation={}, memberId={}, bookId={}, error={}",
                call.getSignature().getName(), memberId(call), bookId(call), error.getMessage());
    }

    private long memberId(JoinPoint call) {
        return call.getArgs()[0] instanceof BorrowingRequest request
                ? request.memberId() : (long) call.getArgs()[0];
    }

    private long bookId(JoinPoint call) {
        return call.getArgs()[0] instanceof BorrowingRequest request
                ? request.bookId() : (long) call.getArgs()[1];
    }
}
