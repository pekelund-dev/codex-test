# GitHub Issue Templates — Architecture Review

> Generated from [`docs/architecture-review.md`](docs/architecture-review.md) (2026-02-27).
> Previous AI ticket board moved to [`OLD_TICKETS.md`](OLD_TICKETS.md).
>
> **How to use:** Copy–paste each template into a new GitHub issue.
> Adjust labels, assignees, and milestones to fit your workflow.

---

## Phase 1 — Quick Wins







## Phase 2 — Code Quality







## Phase 3 — CI/CD Maturity

### Issue: Add post-deployment smoke tests

~~~markdown
**Title:** Add post-deployment smoke tests

**Labels:** ci/cd, testing

**Description**
The deploy workflow pushes to Cloud Run but never verifies the services are healthy.
Add a post-deployment health check and basic functionality verification.

**Tasks**
- [ ] Add a post-deploy step to `deploy-cloud-run.yml` that curls the health endpoint
- [ ] Optionally verify the login page renders (HTTP 200 on `/login`)
- [ ] Fail the workflow if smoke tests fail

**Acceptance criteria**
- Deployment workflow fails if service is unhealthy after deploy
- Smoke test results visible in workflow logs

**References**
- docs/architecture-review.md § 6.2 — CI/CD issues, item 3
~~~

---

### Issue: Auto-deploy to test environment on merge to main

~~~markdown
**Title:** Auto-deploy to test environment on merge to main

**Labels:** ci/cd

**Description**
Both deployment workflows require manual dispatch. Add an automatic deployment
to a test/staging environment on merge to `main`.

**Tasks**
- [ ] Create a test/staging Cloud Run service (or use revision tags)
- [ ] Add a workflow triggered on push to `main` that deploys to test
- [ ] Include smoke tests in the auto-deploy workflow

**Acceptance criteria**
- Merging to `main` triggers a deployment to the test environment
- Deployment includes health verification

**References**
- docs/architecture-review.md § 6.2 — CI/CD issues, item 4
~~~

---

### Issue: Add Dependabot or Renovate for dependency updates

~~~markdown
**Title:** Add Dependabot or Renovate for automated dependency updates

**Labels:** ci/cd, security

**Description**
No automated dependency update mechanism exists. Add Dependabot or Renovate
to keep Maven, npm, and GitHub Actions dependencies current.

**Tasks**
- [ ] Add `.github/dependabot.yml` (or Renovate config)
- [ ] Configure for Maven (`pom.xml`), npm (`web/package.json`), and GitHub Actions
- [ ] Set a reasonable update schedule (e.g. weekly)

**Acceptance criteria**
- Dependency update PRs are created automatically
- At least Maven and npm ecosystems covered

**References**
- docs/architecture-review.md § 6.3 — Recommended CI/CD Improvements
~~~

---

### Issue: Add Artifact Registry vulnerability scanning

~~~markdown
**Title:** Enable Artifact Registry vulnerability scanning for container images

**Labels:** security, ci/cd

**Description**
Container images are pushed to Artifact Registry without vulnerability scanning.
Enable the built-in scanning feature.

**Tasks**
- [ ] Enable vulnerability scanning on the Artifact Registry repository
- [ ] Optionally add a CI step to check scan results before deployment

**Acceptance criteria**
- Pushed images are automatically scanned
- Critical/high vulnerabilities are visible in the GCP console

**References**
- docs/architecture-review.md § 8.1 — Security concerns, item 4
~~~

---

### Issue: Add contract tests between web and receipt-parser

~~~markdown
**Title:** Add contract tests for web ↔ receipt-parser API

**Labels:** testing, ci/cd

**Description**
The web service calls receipt-parser via REST with no contract tests.
API drift could cause silent failures.

**Tasks**
- [ ] Choose a contract testing approach (Spring Cloud Contract or Pact)
- [ ] Add producer-side contract verification in receipt-parser
- [ ] Add consumer-side stub tests in web
- [ ] Wire into CI

**Acceptance criteria**
- Tests validate request/response shape for key endpoints
- CI fails on contract violations

**References**
- docs/architecture-review.md § 7.4 — Key Testing Gaps, item 3
- OLD_TICKETS.md — Ticket O2
~~~

---

## Phase 4 — UI Enhancement

### Issue: Add HTMX for progressive enhancement

~~~markdown
**Title:** Add HTMX for progressive enhancement

**Labels:** ui, frontend

**Description**
Replace full page reloads and custom polling JavaScript with HTMX partial updates.
This is the recommended incremental UI upgrade over switching to React.

**Tasks**
- [ ] Add HTMX dependency (CDN or npm)
- [ ] Replace polling in `receipts.js` with `hx-trigger="every 2s"`
- [ ] Replace scope toggle reloads with `hx-swap`
- [ ] Replace filter form submissions with HTMX partial updates
- [ ] Add HTMX-compatible controller endpoints returning HTML fragments

**Acceptance criteria**
- Scope toggles and filters update without full page reload
- Receipt polling works via HTMX
- Fallback behaviour preserved for non-JS clients

**References**
- docs/architecture-review.md § 4.5 — Option B: Add HTMX
~~~

---

### Issue: Add mobile card view for receipts

~~~markdown
**Title:** Add mobile card view for receipt lists

**Labels:** ui, mobile

**Description**
Receipt tables with many columns require horizontal scrolling on mobile.
Add a card-based layout for screens below 768px.

**Tasks**
- [ ] Create a responsive card component for receipts
- [ ] Show cards on mobile, table on desktop (Bootstrap breakpoints)
- [ ] Ensure all key receipt data visible in card view

**Acceptance criteria**
- Receipts are readable on a 375px-wide screen without horizontal scrolling
- Desktop view unchanged

**References**
- docs/architecture-review.md § 4.3 — Responsive Design Assessment
~~~

---

### Issue: Add dark mode support

~~~markdown
**Title:** Add dark mode using Bootstrap 5.3 data-bs-theme

**Labels:** ui, frontend

**Description**
Bootstrap 5.3+ natively supports dark mode via `data-bs-theme="dark"`.
Add a toggle and persist the user's preference.

**Tasks**
- [ ] Add a dark mode toggle to the navbar
- [ ] Use `data-bs-theme` attribute on `<html>`
- [ ] Persist preference in `localStorage`
- [ ] Ensure all custom CSS respects the theme

**Acceptance criteria**
- Users can toggle between light and dark mode
- Preference persists across page reloads

**References**
- docs/architecture-review.md § 4.6 — UI Improvement Suggestions, item 4
~~~

---

### Issue: Replace splash screen with skeleton loading

~~~markdown
**Title:** Replace splash screen with skeleton loading patterns

**Labels:** ui, frontend

**Description**
The current splash screen delays content display. Replace with skeleton loading
placeholders that give faster perceived performance.

**Tasks**
- [ ] Create skeleton loading CSS for tables, cards, and statistics
- [ ] Replace the splash screen overlay with skeleton placeholders
- [ ] Show real content as soon as data is available

**Acceptance criteria**
- No full-screen splash screen on page load
- Content areas show skeleton placeholders while loading

**References**
- docs/architecture-review.md § 4.6 — UI Improvement Suggestions, item 2
~~~

---

### Issue: Accessibility audit and improvements

~~~markdown
**Title:** Accessibility audit — ARIA labels, keyboard navigation, screen reader support

**Labels:** ui, accessibility

**Description**
No accessibility audit has been performed. Ensure the app meets WCAG 2.1 AA basics.

**Tasks**
- [ ] Run axe-core or Lighthouse accessibility audit on all pages
- [ ] Add ARIA labels to interactive elements (buttons, forms, toggles)
- [ ] Ensure keyboard navigation works throughout
- [ ] Fix colour contrast issues
- [ ] Test with a screen reader

**Acceptance criteria**
- No critical or serious axe/Lighthouse accessibility violations
- All interactive elements reachable by keyboard

**References**
- docs/architecture-review.md § 4.6 — UI Improvement Suggestions, item 6
~~~

---

## Phase 5 — Operational Maturity

### Issue: Automate Firestore backups via Cloud Scheduler

~~~markdown
**Title:** Automate Firestore backups via Cloud Scheduler

**Labels:** operations, data

**Description**
Firestore backups currently require manual admin action. Automate daily
exports using Cloud Scheduler + Cloud Functions or a Cloud Run job.

**Tasks**
- [ ] Create a Cloud Function or Cloud Run job that exports Firestore to GCS
- [ ] Configure Cloud Scheduler to trigger daily exports
- [ ] Add Terraform config for the scheduler and function
- [ ] Add lifecycle rules to clean up old exports

**Acceptance criteria**
- Firestore is automatically exported daily
- Old exports cleaned up per retention policy

**References**
- docs/architecture-review.md § 9.2 — Operational Concerns, item 2
~~~

---

### Issue: Add Cloud Monitoring alerts

~~~markdown
**Title:** Add Cloud Monitoring alerts for errors, latency, and failed deployments

**Labels:** operations, observability

**Description**
No alerting exists for application errors, latency spikes, or failed deployments.

**Tasks**
- [ ] Create Cloud Monitoring alert policies for:
  - Error rate exceeding threshold
  - P95 latency exceeding threshold
  - Service unavailability
- [ ] Configure notification channels (email or Slack)
- [ ] Add Terraform config for alert policies

**Acceptance criteria**
- Alerts fire on elevated error rates or latency
- Notifications delivered to configured channels

**References**
- docs/architecture-review.md § 9.2 — Operational Concerns, item 3
~~~

---

### Issue: Add structured logging with metric extraction

~~~markdown
**Title:** Add structured logging with custom metric extraction

**Labels:** operations, observability

**Description**
Application logs go to Cloud Logging but no custom metrics are extracted.
Add structured logging for key operations.

**Tasks**
- [ ] Switch to structured JSON logging format
- [ ] Add log-based metrics for receipt uploads, parsing, and login events
- [ ] Create a Cloud Monitoring dashboard for the custom metrics

**Acceptance criteria**
- Logs are in structured JSON format
- Key operations have corresponding log-based metrics

**References**
- docs/architecture-review.md § 9.2 — Operational Concerns, item 4
~~~

---

### Issue: Add Playwright E2E tests for critical user journeys

~~~markdown
**Title:** Add Playwright E2E tests for critical user journeys

**Labels:** testing, e2e

**Description**
Zero end-to-end tests exist. Add Playwright tests for the most critical flows.

**Tasks**
- [ ] Set up Playwright in the project
- [ ] Add E2E test: login → dashboard
- [ ] Add E2E test: upload receipt → verify processing
- [ ] Add E2E test: search receipts → view detail
- [ ] Wire into CI (optionally on a schedule or deploy trigger)

**Acceptance criteria**
- `npx playwright test` runs and passes
- At least 3 critical user journeys covered

**References**
- docs/architecture-review.md § 7.4 — Key Testing Gaps, item 2
~~~

---

### Issue: Add rate limiting on authentication and search endpoints

~~~markdown
**Title:** Add rate limiting on authentication and search endpoints

**Labels:** security

**Description**
Login, registration, and search endpoints have no rate limiting, leaving
them vulnerable to brute-force and denial-of-service attacks.

**Tasks**
- [ ] Add rate limiting (Spring Security, Bucket4j, or Cloud Armor)
- [ ] Configure limits for `/login`, `/register`, and `/receipts/search`
- [ ] Return 429 Too Many Requests when limit exceeded
- [ ] Add tests for rate limiting behaviour

**Acceptance criteria**
- Excessive requests to auth/search endpoints are rejected with 429
- Normal usage unaffected

**References**
- docs/architecture-review.md § 8.1 — Security concerns, item 2
~~~

---

## Phase 6 — Architecture Evolution (Optional)

### Issue: Evaluate Cloud SQL for receipt data

~~~markdown
**Title:** Evaluate Cloud SQL (PostgreSQL) for structured receipt data

**Labels:** architecture, data, evaluation

**Description**
Relational queries on Firestore (date+owner+store filtering) require growing
composite indexes. Evaluate whether Cloud SQL would simplify data access.

**Tasks**
- [ ] Prototype receipt schema in PostgreSQL
- [ ] Compare query complexity and performance for common filter patterns
- [ ] Estimate cost difference (Firestore reads vs Cloud SQL instance)
- [ ] Document findings and recommendation

**Acceptance criteria**
- Written comparison document with cost and complexity analysis
- Clear recommendation (migrate or stay)

**References**
- docs/architecture-review.md § 5.2 — Data Storage Assessment
~~~

---

### Issue: Migrate to Firestore subcollections for receipt items

~~~markdown
**Title:** Migrate receipt items to Firestore subcollections

**Labels:** architecture, data

**Description**
All data is in top-level collections. Receipt items could be subcollections
under receipts, reducing query scope and improving performance.

**Tasks**
- [ ] Design subcollection structure (receipts/{id}/items)
- [ ] Write migration script for existing data
- [ ] Update all queries to use subcollection paths
- [ ] Update tests

**Acceptance criteria**
- Receipt items stored as subcollections
- Existing queries work correctly with new structure
- Data migration completed for existing receipts

**References**
- docs/architecture-review.md § 5.2 — Assessment, item 2
~~~

---

### Issue: Evaluate React for dashboard (learning goal)

~~~markdown
**Title:** Evaluate React frontend for the dashboard module (if learning React is a goal)

**Labels:** architecture, frontend, evaluation

**Description**
If learning React is a goal, consider a hybrid approach: keep Thymeleaf for
landing pages and build a React SPA for the dashboard/workspace.

**Tasks**
- [ ] Create a `frontend/` module with React + Vite
- [ ] Prototype the dashboard view in React
- [ ] Add a REST/GraphQL API layer for dashboard data
- [ ] Document the hybrid architecture approach

**Acceptance criteria**
- React dashboard renders receipt data from the API
- Thymeleaf pages still work for non-dashboard routes
- Both build pipelines integrated in CI

**References**
- docs/architecture-review.md § 4.5 — Option C: Switch to React
~~~
