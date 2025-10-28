package dev.pekelund.pklnd.firestore;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class FirestoreReadTotals {

    private final AtomicLong totalReads = new AtomicLong();

    public void increment() {
        increment(1L);
    }

    public void increment(long amount) {
        if (amount <= 0) {
            return;
        }
        totalReads.addAndGet(amount);
    }

    public long getTotalReads() {
        return totalReads.get();
    }
}
