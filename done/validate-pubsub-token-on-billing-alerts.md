# Validate Pub/Sub push subscription token on /api/billing/alerts

**Labels:** security, quick-win

**Description**
The `/api/billing/alerts` endpoint is publicly accessible and accepts Pub/Sub messages
without token verification. A malicious actor could send fake billing alerts to
trigger a service shutdown.

**Tasks**
- [x] Add bearer token validation via `BILLING_ALERT_TOKEN` query parameter
- [x] Update Terraform Pub/Sub subscription config to include the verification token
- [x] Add tests that reject requests without a valid token (3 tests: valid/invalid/missing)
- [x] Update `docs/setup-budget-alert-handling.md` with the new token configuration

**Acceptance criteria**
- [x] Requests without a valid token receive 403 (when BILLING_ALERT_TOKEN is set)
- [x] Legitimate Pub/Sub messages still processed successfully (token in URL query param)
- [x] Token defaults to empty (validation disabled) for backwards compatibility

**References**
- docs/architecture-review.md § 8.1 — Security concerns, item 1
- web/src/main/java/dev/pekelund/pklnd/config/SecurityConfig.java
