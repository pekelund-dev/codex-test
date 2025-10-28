package dev.pekelund.pklnd.firestore;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

        log.info(
            "Firestore read summary for {} {}: {} read(s). Operations: {}",
            request.getMethod(),
            request.getRequestURI(),
            count,
            tracker.getReadOperations()
                .stream()
                .map(operation ->
                    operation.readUnits() <= 1
                        ? operation.description()
                        : operation.description() + " (" + operation.readUnits() + ")")
                .collect(Collectors.joining("; "))
        );
    }
}
