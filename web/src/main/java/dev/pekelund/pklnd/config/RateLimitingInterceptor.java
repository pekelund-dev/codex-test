package dev.pekelund.pklnd.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Simple in-memory rate limiter using a sliding window per client IP.
 * Applies to authentication and search endpoints to mitigate brute-force attacks.
 *
 * <p>Limits are intentionally generous to avoid disrupting normal usage.
 * For production environments with multiple instances, consider Redis-backed rate limiting.
 */
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitingInterceptor.class);

    /** Maximum number of requests per window per IP. */
    private final int maxRequests;

    /** Window size in milliseconds. */
    private final long windowMs;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitingInterceptor(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    @Override
    public boolean preHandle(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Object handler
    ) throws Exception {
        String clientIp = resolveClientIp(request);
        WindowCounter counter = counters.computeIfAbsent(clientIp, k -> new WindowCounter());

        if (!counter.tryIncrement(maxRequests, windowMs)) {
            LOGGER.warn("Rate limit exceeded for IP {} on {}", clientIp, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Too many requests. Please try again later.");
            return false;
        }
        return true;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = Instant.now().toEpochMilli();

        synchronized boolean tryIncrement(int maxRequests, long windowMs) {
            long now = Instant.now().toEpochMilli();
            if (now - windowStart > windowMs) {
                count.set(0);
                windowStart = now;
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }
}
