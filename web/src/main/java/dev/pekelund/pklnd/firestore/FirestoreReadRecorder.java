package dev.pekelund.pklnd.firestore;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Bridges the request-scoped {@link FirestoreReadTracker} with the singleton
 * {@link FirestoreReadTotals}. This ensures we always capture Firestore read
 * activity even when a request scope is unavailable (for example in
 * background jobs or authentication flows executed outside the MVC
 * dispatcher).
 */
@Component
public class FirestoreReadRecorder {

    private final ObjectProvider<FirestoreReadTracker> trackerProvider;
    private final FirestoreReadTotals totals;

    public FirestoreReadRecorder(
        ObjectProvider<FirestoreReadTracker> trackerProvider,
        FirestoreReadTotals totals
    ) {
        this.trackerProvider = trackerProvider;
        this.totals = totals;
    }

    public void record(String description) {
        record(description, 1L);
    }

    public void record(String description, long readUnits) {
        long units = Math.max(0L, readUnits);
        FirestoreReadTracker tracker = trackerProvider.getIfAvailable();
        if (tracker != null) {
            tracker.recordRead(description, units);
            return;
        }

        if (units > 0) {
            totals.increment(units);
        }
    }
}
