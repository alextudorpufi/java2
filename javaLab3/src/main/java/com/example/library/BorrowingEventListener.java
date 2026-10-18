package com.example.library;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BorrowingEventListener {
    private static final Logger log = LoggerFactory.getLogger(BorrowingEventListener.class);

    @EventListener
    public void onThresholdExceeded(BorrowingThresholdExceeded event) {
        log.info("Borrowing event: member={}, book={}, activeLoans={}, threshold={}",
                event.member().name(), event.book().title(), event.activeLoans(), event.threshold());
    }
}
