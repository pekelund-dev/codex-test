# Add rate limiting on authentication and search endpoints

**Status:** Completed

- [x] RateLimitingInterceptor.java: in-memory sliding window per IP
- [x] RateLimitingConfig.java: 20 req/min on /login, /register; 60 req/min on /receipts/search
- [x] Returns HTTP 429 when limit exceeded
- [x] RateLimitingInterceptorTest.java: 3 tests (82 total pass)
