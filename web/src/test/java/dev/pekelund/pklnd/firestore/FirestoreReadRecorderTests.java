package dev.pekelund.pklnd.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class FirestoreReadRecorderTests {

    @Test
    void delegatesToTrackerWhenAvailable() {
        FirestoreReadTotals totals = new FirestoreReadTotals();
        FirestoreReadTracker tracker = new FirestoreReadTracker(totals);
        @SuppressWarnings("unchecked")
        ObjectProvider<FirestoreReadTracker> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tracker);

        FirestoreReadRecorder recorder = new FirestoreReadRecorder(provider, totals);
        recorder.record("Test operation", 5L);

        assertThat(tracker.getReadCount()).isEqualTo(5L);
        assertThat(totals.getTotalReads()).isEqualTo(5L);
    }

    @Test
    void incrementsTotalsWhenTrackerMissing() {
        FirestoreReadTotals totals = new FirestoreReadTotals();
        @SuppressWarnings("unchecked")
        ObjectProvider<FirestoreReadTracker> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        FirestoreReadRecorder recorder = new FirestoreReadRecorder(provider, totals);
        recorder.record("Background task", 3L);

        assertThat(totals.getTotalReads()).isEqualTo(3L);
    }
}
