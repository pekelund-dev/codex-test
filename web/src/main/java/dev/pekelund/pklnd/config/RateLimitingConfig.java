package dev.pekelund.pklnd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link RateLimitingInterceptor} for authentication and search endpoints.
 *
 * <p>Limits: 20 requests per minute per IP on auth endpoints; 60 per minute on search.
 */
@Configuration
public class RateLimitingConfig implements WebMvcConfigurer {

    private static final int AUTH_MAX = 20;
    private static final long ONE_MINUTE_MS = 60_000L;
    private static final int SEARCH_MAX = 60;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitingInterceptor(AUTH_MAX, ONE_MINUTE_MS))
            .addPathPatterns("/login", "/register", "/login/**");

        registry.addInterceptor(new RateLimitingInterceptor(SEARCH_MAX, ONE_MINUTE_MS))
            .addPathPatterns("/receipts/search");
    }
}
