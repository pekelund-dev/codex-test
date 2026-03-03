package dev.pekelund.pklnd.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class FirestoreReadRecorderTests {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void delegatesToTrackerWhenRequestScopeIsActive() {
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(new MockHttpServletRequest()));

        FirestoreReadTotals totals = new FirestoreReadTotals();
        FirestoreReadTracker tracker = new FirestoreReadTracker(totals);
        @SuppressWarnings("unchecked")
        ObjectProvider<FirestoreReadTracker> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tracker);

        FirestoreReadRecorder recorder = new FirestoreReadRecorder(provider, totals);
        recorder.record("Test operation", 5L);

        assertThat(tracker.getReadCount()).isEqualTo(5L);
        assertThat(totals.getTotalReads()).isEqualTo(5L);
        verify(provider, never()).getObject();
    }

    @Test
    void incrementsTotalsWhenTrackerUnavailableInRequestScope() {
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(new MockHttpServletRequest()));

        FirestoreReadTotals totals = new FirestoreReadTotals();
        @SuppressWarnings("unchecked")
        ObjectProvider<FirestoreReadTracker> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        FirestoreReadRecorder recorder = new FirestoreReadRecorder(provider, totals);
        recorder.record("Request task", 3L);

        assertThat(totals.getTotalReads()).isEqualTo(3L);
    }

    @Test
    void incrementsTotalsWhenNoRequestScopeActive() {
        FirestoreReadTotals totals = new FirestoreReadTotals();
        @SuppressWarnings("unchecked")
        ObjectProvider<FirestoreReadTracker> provider = mock(ObjectProvider.class);

        FirestoreReadRecorder recorder = new FirestoreReadRecorder(provider, totals);
        recorder.record("Background task", 3L);

        assertThat(totals.getTotalReads()).isEqualTo(3L);
        verify(provider, never()).getIfAvailable();
    }
}
