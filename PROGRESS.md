# Validate Pub/Sub push subscription token on /api/billing/alerts

**Labels:** security, quick-win

**Description**
The `/api/billing/alerts` endpoint is publicly accessible and accepts Pub/Sub messages
without token verification. A malicious actor could send fake billing alerts to
trigger a service shutdown.

**Tasks**
- [ ] Add bearer token or audience validation for Pub/Sub push subscriptions
- [ ] Update Terraform Pub/Sub subscription config to include the verification token
- [ ] Add a test that rejects requests without a valid token
- [ ] Update `docs/setup-budget-alert-handling.md` with the new token configuration

**Acceptance criteria**
- Requests without a valid token receive 403
- Legitimate Pub/Sub messages still processed successfully

**References**
- docs/architecture-review.md § 8.1 — Security concerns, item 1
- web/src/main/java/dev/pekelund/pklnd/config/SecurityConfig.java
