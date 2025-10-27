package dev.pekelund.pklnd.firestore;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class FirestoreReadTotals {

    private final AtomicLong totalReads = new AtomicLong();

    public void increment() {
        totalReads.incrementAndGet();
    }

    public long getTotalReads() {
        return totalReads.get();
    }
}
