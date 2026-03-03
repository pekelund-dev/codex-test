package dev.pekelund.pklnd.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RateLimitingInterceptor} verifying that excessive requests
 * return HTTP 429 Too Many Requests.
 */
class RateLimitingInterceptorTest {

    @Test
    void rateLimiterAllowsRequestsWithinLimit() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(2, 60_000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void rateLimiterReturnsTooManyRequestsAfterExceedingLimit() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(2, 60_000L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.preHandle(request, response, new Object());

        // Third request should be rejected
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void rateLimiterUsesXForwardedForHeader() throws Exception {
        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(1, 60_000L);

        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/login");
        request1.addHeader("X-Forwarded-For", "10.0.0.1");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/login");
        request2.addHeader("X-Forwarded-For", "10.0.0.2");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        // Different IPs should have separate counters
        assertThat(interceptor.preHandle(request1, response1, new Object())).isTrue();
        assertThat(interceptor.preHandle(request2, response2, new Object())).isTrue();
    }
}
