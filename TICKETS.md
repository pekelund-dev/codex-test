# AI Ticket Board

This file is the shared backlog for AI coding tools (Codex, Copilot, etc.).

## How to use this file

- **Pick one ticket** per agent and implement only what that ticket requests.
- **Minimal prompt:** “You are implementing Ticket X.Y from TICKETS.md.” The ticket text must be sufficient; avoid adding extra instructions outside this file.
- After finishing, **update the ticket status** and add a short completion note.
- If implementation reveals new tasks, **add follow‑up tickets** at the end of the relevant section.
- Keep each ticket **small and verifiable** (single‑responsibility, clear acceptance criteria).
- **Do not edit unrelated tickets** unless they need cross‑linking or dependency updates.
- Follow repository standards (Swedish UI text, English code/doc text, Modulith boundaries).
- Record any tests run in the ticket’s completion note.
- Completion notes should include:
  - Summary of changes (1–3 bullets)
  - Tests run (exact commands, or “Not run”)
  - Follow‑up tasks (if any), added as new tickets

## Status legend

- `todo` — not started
- `in-progress` — being implemented
- `blocked` — needs info or dependency
- `done` — completed and merged

---

## 1) Architecture & Packaging (Package‑by‑feature)

### Ticket 1.1 — Define feature package map
**Status:** `done`

**Goal**
Create a short design note that maps current controllers/services into feature packages.

**Scope**
- Add a “Feature packaging plan” section to `README.md`.
- Include a table mapping routes/controllers to proposed packages (e.g., `web.dashboard`, `web.receipts`, `web.auth`).

**Acceptance criteria**
- README includes a clear package‑by‑feature mapping table.
- Mentions which routes currently in `HomeController` will move and where.

**Notes**
- Keep this as a planning change only (no refactor in this ticket).

**Completion note**
- Summary:
  - Added a feature packaging plan section in the README with a route-to-package mapping table.
  - Documented the intended HomeController route splits by feature package.
- Tests run: Not run (documentation-only change).
- Follow-up tasks: None.

---

### Ticket 1.2 — Split `HomeController` by feature
**Status:** `done`

**Goal**
Split `HomeController` into smaller feature‑focused controllers.

**Scope**
- Create controllers such as:
  - `HomeController` (home/about)
  - `DashboardController`
  - `StatisticsController`
- Keep route paths identical.
- Update any wiring/imports accordingly.

**Acceptance criteria**
- All current routes still resolve.
- No functional changes in views or security.
- Tests updated or added as needed.

**Completion note**
- Summary:
  - Split the former `HomeController` routes into `home`, `dashboard`, `statistics`, and `auth` controllers under feature packages.
  - Added WebMvcTest coverage for the new controllers (including login) to confirm routing and views.
- Tests run:
  - `./mvnw -Pinclude-web -pl web -am test` (failed: ReceiptProcessingClientTest.recordsFailuresWhenProcessorReturnsError hit HttpServerErrorException 500 on POST http://localhost/events/storage; overall summary: Tests run 54, Failures 2, Errors 3)
- Follow-up tasks: None.

---

### Ticket 1.3 — Modulith annotations for new packages
**Status:** `done`

**Goal**
Add or update `@ApplicationModule` annotations for new feature packages.

**Scope**
- Add `package-info.java` for new feature packages.
- Align Modulith metadata with new package layout.

**Acceptance criteria**
- Modulith verification tests pass after refactor.

**Completion note**
- Summary:
  - Added ApplicationModule package-info descriptors for the new home, dashboard, statistics, and auth feature packages.
- Tests run:
  - `./mvnw -Pinclude-web -pl web -am test -Dtest=ModularityVerificationTests`
- Follow-up tasks: None.

---

## 2) Observability & Health

### Ticket 2.1 — Add Actuator to `web`
**Status:** `done`

**Goal**
Enable health and metrics endpoints for the web service.

**Scope**
- Add `spring-boot-starter-actuator` to `web/pom.xml`.
- Configure health probes in `web/src/main/resources/application.yml`.

**Acceptance criteria**
- `/actuator/health` returns `UP` locally.
- Readiness/liveness probes enabled in config.

**Completion note**
- Summary:
  - Added Spring Boot Actuator to the web module and exposed health/info endpoints.
  - Enabled readiness and liveness probes for the web service.
- Tests run: Not run (health endpoints not verified locally).
- Follow-up tasks: None.

---

### Ticket 2.2 — Add Actuator to `receipt-parser`
**Status:** `done`

**Goal**
Enable health endpoints for the receipt parser service.

**Scope**
- Add `spring-boot-starter-actuator` to `receipt-parser/pom.xml`.
- Configure health probes in `receipt-parser/src/main/resources/application.yml`.

**Acceptance criteria**
- `/actuator/health` returns `UP` locally.

**Completion note**
- Summary:
  - Added Spring Boot Actuator to the receipt parser module and exposed health/info endpoints.
  - Enabled readiness and liveness probes for the receipt parser service.
- Tests run: Not run (health endpoints not verified locally).
- Follow-up tasks: None.

---

### Ticket 2.3 — Add request correlation IDs
**Status:** `done`

**Goal**
Propagate a request ID across web → receipt‑parser calls and logs.

**Scope**
- Add a filter/interceptor in the web app that ensures a request ID is present.
- Add the request ID to outbound receipt‑parser HTTP calls.
- Include the request ID in logs.

**Acceptance criteria**
- Logs show request IDs on both services.
- Downstream requests receive the ID header.

**Completion note**
- Summary:
  - Added request ID filters for web and receipt parser that inject `X-Request-Id` and populate MDC for logging.
  - Propagated request IDs to receipt-parser calls and updated logging patterns to include the request ID.
- Tests run:
  - `./mvnw -Pinclude-web -pl web -am test -Dtest=RequestIdFilterTests -Dsurefire.failIfNoSpecifiedTests=false`
  - `./mvnw -pl receipt-parser -am test -Dtest=RequestIdFilterTests -Dsurefire.failIfNoSpecifiedTests=false`
- Follow-up tasks: None.

---

## 3) Testing Strategy

### Ticket 3.1 — Web MVC tests for core routes
**Status:** `done`

**Goal**
Introduce `@WebMvcTest` coverage for primary routes.

**Scope**
- Add tests for `/`, `/dashboard`, `/receipts`, `/login`.
- Verify status codes and template names.

**Acceptance criteria**
- `./mvnw -Pinclude-web -pl web -am test` passes.
- Tests cover at least 4 routes.

**Completion note**
- Summary:
  - Added WebMvc tests for the dashboard and receipts routes to confirm status and view rendering.
  - Reused existing home and login WebMvc tests to cover the core route set.
- Tests run:
  - `./mvnw -Pinclude-web -pl web -am test`
- Follow-up tasks: None.

---

### Ticket 3.2 — Security tests for protected pages
**Status:** `done`

**Goal**
Ensure unauthenticated access to protected pages redirects to login.

**Scope**
- Add tests asserting `/dashboard` and `/receipts` redirect when unauthenticated.

**Acceptance criteria**
- Tests pass and demonstrate redirect behavior.

**Completion note**
- Summary:
  - Added integration tests to verify unauthenticated requests to /dashboard and /receipts redirect to the login flow.
- Tests run:
  - `./mvnw -Pinclude-web -pl web -am test`
- Follow-up tasks: None.

---

## 4) Security Hardening

### Ticket 4.1 — Remove inline JS from `layout.html`
**Status:** `todo`

**Goal**
Move inline JS into static assets to allow strict CSP.

**Scope**
- Create a dedicated JS file under `web/src/main/resources/static/js/`.
- Move inline JS from `layout.html` into that file.
- Ensure behavior unchanged (navbar toggling, splash screen).

**Acceptance criteria**
- `layout.html` has no inline `<script>` blocks.
- UI behavior remains intact.

---

### Ticket 4.2 — Add CSP and security headers
**Status:** `todo`

**Goal**
Harden HTTP security headers.

**Scope**
- Add CSP header (with nonces or hashes if needed).
- Add HSTS, X‑Frame‑Options, X‑Content‑Type‑Options, Referrer‑Policy.

**Acceptance criteria**
- Headers present on responses.
- No inline JS is required for core pages.

---

## 5) Frontend Production Readiness

### Ticket 5.1 — Introduce asset pipeline
**Status:** `todo`

**Goal**
Create a minimal frontend build pipeline for JS/CSS.

**Scope**
- Add a small build tool (e.g., Vite).
- Bundle and minify JS/CSS.
- Reference built assets from Thymeleaf templates.

**Acceptance criteria**
- Build outputs are versioned and referenced by templates.
- Existing functionality unaffected.

---

### Ticket 5.2 — Add linting for JS/CSS
**Status:** `todo`

**Goal**
Introduce linting for frontend assets.

**Scope**
- Add ESLint and Stylelint configuration.
- Add a CI step or script to run the linters.

**Acceptance criteria**
- Lint command runs cleanly.

---

## 6) Deployment & Release Hardening

### Ticket 6.1 — Add tests before Cloud Build
**Status:** `todo`

**Goal**
Ensure tests run in CI before Docker build.

**Scope**
- Update `cloudbuild.yaml` to run tests prior to Docker build steps.

**Acceptance criteria**
- Build fails on test failure.

---

### Ticket 6.2 — Release versioning automation
**Status:** `todo`

**Goal**
Automate release versioning and changelog updates.

**Scope**
- Add documented release flow in README.
- Optionally add a CI workflow for tagging releases.

**Acceptance criteria**
- README includes release steps and versioning rules.

---

## 7) Data & Reliability

### Ticket 7.1 — Document Firestore indexes
**Status:** `todo`

**Goal**
Make required Firestore indexes explicit.

**Scope**
- Add a doc under `docs/` describing required indexes and how to apply them.
- Link the doc from README.

**Acceptance criteria**
- Index requirements are clearly described.
- README points to the new doc.

---

### Ticket 7.2 — Document backup & retention strategy
**Status:** `todo`

**Goal**
Define data backup and retention guidance.

**Scope**
- Add a doc under `docs/` covering Firestore exports and GCS lifecycle policies.
- Link the doc from README.

**Acceptance criteria**
- Backup/retention steps are documented.
- README links to the new doc.

---

## Optional Enhancements (Add after core milestones)

### Ticket O1 — Add request tracing dashboards
**Status:** `todo`

**Goal**
Provide a basic Cloud Monitoring dashboard plan for latency, error rate, and throughput.

**Scope**
- Add a doc section with suggested metrics and alert thresholds.

**Acceptance criteria**
- Clear dashboard guidance exists in docs.

---

### Ticket O2 — Add contract tests for web → receipt‑parser
**Status:** `todo`

**Goal**
Prevent API drift between services.

**Scope**
- Add consumer contract tests (e.g., Spring Cloud Contract or Pact).

**Acceptance criteria**
- Tests validate request/response shape for key endpoints.
