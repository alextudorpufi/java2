package com.example.library;

import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class LoanRepository {
    private final Map<Long, Loan> loans = new HashMap<>();

    // The validation aspect uses this same monitor across validation and mutation.
    public synchronized Optional<Loan> findByBookId(long bookId) {
        return Optional.ofNullable(loans.get(bookId));
    }
    public synchronized long countByMemberId(long memberId) {
        return loans.values().stream().filter(loan -> loan.memberId() == memberId).count();
    }
    public synchronized List<Loan> findAll() { return List.copyOf(loans.values()); }
    synchronized void add(Loan loan) { loans.put(loan.bookId(), loan); }
    synchronized void remove(long bookId) { loans.remove(bookId); }
}
