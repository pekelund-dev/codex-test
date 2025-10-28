package dev.pekelund.pklnd.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FirestoreReadTrackerTests {

    @Test
    void recordReadAccumulatesReadUnitsAndTotals() {
        FirestoreReadTotals totals = new FirestoreReadTotals();
        FirestoreReadTracker tracker = new FirestoreReadTracker(totals);

        tracker.recordRead("Single read");
        tracker.recordRead("Batch read", 5);
        tracker.recordRead("No documents", 0);

        assertThat(tracker.getReadCount()).isEqualTo(6);
        assertThat(totals.getTotalReads()).isEqualTo(6);
        assertThat(tracker.getReadOperations())
            .extracting(FirestoreReadTracker.ReadOperation::description)
            .containsExactly("Single read", "Batch read", "No documents");
        assertThat(tracker.getReadOperations())
            .extracting(FirestoreReadTracker.ReadOperation::readUnits)
            .containsExactly(1L, 5L, 0L);
    }
}
