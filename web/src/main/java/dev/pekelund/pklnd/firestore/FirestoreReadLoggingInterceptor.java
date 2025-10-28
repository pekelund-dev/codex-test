package dev.pekelund.pklnd.firestore;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class FirestoreReadLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FirestoreReadLoggingInterceptor.class);

    private final ObjectProvider<FirestoreReadTracker> readTrackerProvider;

    public FirestoreReadLoggingInterceptor(ObjectProvider<FirestoreReadTracker> readTrackerProvider) {
        this.readTrackerProvider = readTrackerProvider;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {
        FirestoreReadTracker tracker = readTrackerProvider.getIfAvailable();
        if (tracker == null) {
            return;
        }

        long count = tracker.getReadCount();
        if (count == 0) {
            return;
        }

        String operationsSummary = formatOperations(tracker.getReadOperations());

        log.info(
            "Firestore read summary for {} {}: {} read(s). Operations: {}",
            request.getMethod(),
            request.getRequestURI(),
            count,
            operationsSummary
        );
    }

    private String formatOperations(List<FirestoreReadTracker.ReadOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return "<no tracked operations>";
        }

        Map<String, Long> readTotals = new LinkedHashMap<>();
        Map<String, Long> callCounts = new LinkedHashMap<>();

        for (FirestoreReadTracker.ReadOperation operation : operations) {
            readTotals.merge(operation.description(), operation.readUnits(), Long::sum);
            callCounts.merge(operation.description(), 1L, Long::sum);
        }

        return readTotals.entrySet()
            .stream()
            .map(entry -> {
                String description = entry.getKey();
                long reads = entry.getValue();
                long invocations = callCounts.getOrDefault(description, 0L);
                String readsLabel = reads == 1 ? "read" : "reads";
                if (invocations <= 1) {
                    return String.format("%s (%d %s)", description, reads, readsLabel);
                }
                return String.format(
                    "%s (%d %s across %d call%s)",
                    description,
                    reads,
                    readsLabel,
                    invocations,
                    invocations == 1 ? "" : "s"
                );
            })
            .collect(Collectors.joining("; "));
    }
}
