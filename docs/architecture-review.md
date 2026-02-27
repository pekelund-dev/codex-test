# Architecture & Design Review

> **Date:** 2026-02-27
> **Scope:** Full-stack review of architecture, code design, UI, data storage, CI/CD, and testing
> **Goal:** Provide an honest assessment of the project's current state and actionable improvement suggestions

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current Architecture Overview](#2-current-architecture-overview)
3. [Code Design Review](#3-code-design-review)
4. [UI Design Review](#4-ui-design-review)
5. [Data Storage Review](#5-data-storage-review)
6. [CI/CD Pipeline Review](#6-cicd-pipeline-review)
7. [Testing Review](#7-testing-review)
8. [Security Review](#8-security-review)
9. [Cost & Operational Review](#9-cost--operational-review)
10. [Improvement Roadmap](#10-improvement-roadmap)

---

## 1. Executive Summary

The **pklnd** project is a well-structured Swedish receipt archive application built with Spring Boot 3 / Java 21, deployed on Google Cloud Run. It demonstrates a solid grasp of modern Java development, GCP services, and infrastructure-as-code. The architecture is clean for a learning project: two microservices (web + receipt-parser) sharing Firestore and Cloud Storage, with Terraform managing infrastructure.

### Strengths
- **Clean module separation** with Spring Modulith enforcing boundaries between `core`, `web`, and `receipt-parser`
- **Comprehensive infrastructure automation** via Terraform with proper state management
- **Thoughtful cost controls** including automatic billing shutdown, image cleanup, and scale-to-zero
- **Good security posture** with CSP nonces, HSTS, CSRF protection, and proper IAM scoping
- **Extensive documentation** covering deployment, setup, troubleshooting, and architecture

### Areas for Improvement
- **God classes** in `ReceiptController` (2,120 lines) and `ReceiptExtractionService` (851 lines) need splitting
- **No frontend or E2E tests** – Thymeleaf templates and JavaScript are untested
- **UI component duplication** – scope toggles, alert blocks, and table patterns repeated across 10+ templates
- **Missing test coverage tooling** – no JaCoCo or similar to measure what is actually covered
- **Frontend linting not wired into CI** – ESLint/Stylelint exist but are not run in PR validation

---

## 2. Current Architecture Overview

### System Topology

```
┌─────────────────────┐     ┌─────────────────────────┐
│   Browser / Mobile   │     │   Google Cloud Console    │
└─────────┬───────────┘     └────────────┬────────────┘
          │ HTTPS                        │ Budget Alerts
          ▼                              ▼
┌─────────────────────┐     ┌─────────────────────────┐
│    pklnd-web         │     │   Cloud Pub/Sub          │
│  (Cloud Run, 0-10)   │◄────│  (billing-alerts topic)  │
│  Spring Boot + MVC   │     └─────────────────────────┘
│  Thymeleaf + Vite    │
└────┬──────┬──────────┘
     │      │ Signed URL upload
     │      ▼
     │  ┌───────────────────┐
     │  │  Cloud Storage     │
     │  │  (receipts bucket) │
     │  └────────┬──────────┘
     │           │ Storage finalize event
     │           ▼
     │  ┌───────────────────────┐
     │  │  pklnd-receipts        │
     │  │  (Cloud Run, 0-5)      │
     │  │  Spring Boot + AI      │
     │  │  Gemini 2.0 Flash      │
     │  └────────┬──────────────┘
     │           │
     ▼           ▼
┌───────────────────────────┐
│  Firestore (receipts-db)   │
│  users, receiptExtractions │
│  receiptItems, categories  │
│  tags, tagSummaries        │
└───────────────────────────┘
```

### Module Structure

| Module | Artifact | Purpose | Key Dependencies |
|--------|----------|---------|------------------|
| `core` | `pklnd-core` | Shared storage abstractions, domain constants | GCS SDK, Spring Context |
| `web` | `pklnd-web` | MVC web app, security, Firestore user/receipt access | Thymeleaf, Spring Security, OAuth2, Firestore |
| `receipt-parser` | `pklnd-receipt-parser` | AI-powered receipt parsing, REST API | Spring AI, Gemini, PDFBox, Firestore |

### Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.5.7 |
| AI | Spring AI + Gemini | 1.0.3 |
| Modularity | Spring Modulith | 1.4.1 |
| Template Engine | Thymeleaf | (Spring Boot managed) |
| CSS Framework | Bootstrap | 5.3.3 |
| Charts | Chart.js | 4.4.4 |
| Build Tool | Maven | 3.9.9 (wrapper) |
| Asset Pipeline | Vite | 5.4.19 |
| Database | Firestore Native | 3.33.0 SDK |
| Object Storage | Google Cloud Storage | 2.40.1 SDK |
| Infrastructure | Terraform | (latest) |
| CI/CD | GitHub Actions | 3 workflows |
| Container Runtime | Google Cloud Run v2 | n/a |

### Assessment

The architecture is **appropriate for the project's scale and learning goals**. The two-service split (web + receipt-parser) is a good learning exercise for microservice patterns, although a monolith would have been simpler for this use case. The use of Firestore over Cloud SQL is also a learning-oriented choice – a relational database would be more natural for receipt line-items with their relational queries, but Firestore is a good exercise in NoSQL data modelling.

> **Learning note:** Using Firestore for relational-style queries (e.g. filtering receipts by date range, owner, and store) requires careful index management and denormalization. A relational database would simplify these queries significantly, but the current approach is valuable for learning Firestore's strengths and limitations.

---

## 3. Code Design Review

### 3.1 Package Organisation

The project follows a **package-by-feature** approach, which is the correct choice:

```
web/src/main/java/dev/pekelund/pklnd/
├── web/              # Controllers, DTOs, view adapters
│   ├── auth/         # Authentication & registration
│   ├── dashboard/    # Dashboard views
│   ├── home/         # Landing pages
│   ├── statistics/   # Statistics controllers & services
│   └── assets/       # Vite manifest integration
├── firestore/        # Firestore access layer
├── config/           # Cross-cutting configuration
├── receipts/         # Receipt processing client
└── billing/          # Billing shutdown feature
```

The planned feature-package migration in the README is partially complete. The `HomeController` has been split into `HomeController`, `DashboardController`, `StatisticsController`, `LoginController`, and `AuthController` – which is excellent progress.

### 3.2 Critical Code Issues

#### Issue 1: `ReceiptController` is a God Class (2,120 lines)

**Severity:** 🔴 Critical

At 2,120 lines, the `ReceiptController` handles far too many responsibilities:
- Receipt upload (multipart file handling)
- Receipt search and filtering
- Receipt statistics computation
- Dashboard data formatting
- Price calculations and discounting logic
- Inline enum types (`ReceiptViewScope`, `PeriodType`)
- Multiple regex patterns for data extraction

**Recommendation:** Split into focused controllers and services:
- `ReceiptUploadController` – Upload handling, file validation
- `ReceiptSearchController` – Search and filtering
- `ReceiptOverviewController` – Overview and statistics display (partially exists)
- `ReceiptStatisticsService` – Move computation logic out of controllers
- `ReceiptPriceService` – Price calculations and formatting

#### Issue 2: `ReceiptExtractionService` is a God Class (851 lines)

**Severity:** 🔴 Critical

This service combines receipt CRUD operations, search/filtering, item extraction, and EAN deduplication in a single class.

**Recommendation:** Split into:
- `ReceiptRepository` – Pure CRUD operations against Firestore
- `ReceiptSearchService` – Search and filtering logic
- `ReceiptItemService` – Item-level operations and EAN management

#### Issue 3: `FirestoreUserService` is Overloaded (646 lines)

**Severity:** 🟡 High

Combines user registration, authentication, admin role management, and fallback in-memory user store.

**Recommendation:** Split into:
- `FirestoreUserRepository` – CRUD operations
- `UserAuthenticationService` – Authentication and role resolution
- `AdminManagementService` – Admin promotion/demotion

### 3.3 Design Patterns Assessment

| Pattern | Usage | Assessment |
|---------|-------|------------|
| Service Layer | ✅ Used throughout | Good separation of business logic |
| Records for DTOs | ✅ `ParsedReceipt`, `Category`, `ItemTag` | Excellent use of Java 21 records |
| Strategy Pattern | ✅ `ReceiptDataExtractor` interface | Good for multiple parsing strategies |
| Registry Pattern | ✅ `ReceiptParserRegistry` | Clean plugin discovery |
| Optional Injection | ⚠️ `ObjectProvider<T>` throughout | Works but increases testing complexity |
| Interceptor Pattern | ✅ `FirestoreReadLoggingInterceptor` | Good for cross-cutting read tracking |
| Builder Pattern | ✅ Category, ItemTag | Fluent construction |

### 3.4 Code Quality Observations

**Good practices:**
- Consistent use of SLF4J logging with appropriate levels
- Proper use of `@ConfigurationProperties` for typed configuration
- Spring Modulith enforcing module boundaries
- Graceful degradation when GCP services are unavailable (`DisabledReceiptStorageService`)
- Request correlation IDs propagated across services (`X-Request-Id`)

**Areas for improvement:**
- **Blocking Firestore calls** – `ApiFuture.get()` blocks the request thread; consider reactive alternatives or `@Async`
- **Exception swallowing** – Multiple `try/catch` blocks return empty/default values without logging the cause
- **Inconsistent null handling** – Mix of `Optional`, `StringUtils.hasText()`, and raw null checks
- **Hard-coded collection names** – Some collections are configurable via properties, others are hard-coded constants
- **Large method bodies** – Several methods exceed 50 lines; extract private helper methods

---

## 4. UI Design Review

### 4.1 Current Approach

The UI is built with **server-rendered Thymeleaf templates** enhanced by **vanilla JavaScript** bundled through **Vite**. This is a multi-page application (MPA) pattern – each navigation triggers a full page reload, with JavaScript adding progressive enhancements (polling, sorting, drag-drop uploads, charts).

### 4.2 Template Inventory

| Template | Purpose |
|----------|---------|
| `layout.html` | Master layout (navbar, footer, splash screen) |
| `home.html` | Landing page with hero section |
| `login.html` / `register.html` | Authentication forms |
| `dashboard.html` | User dashboard with admin management |
| `receipts.html` | Main receipt archive view |
| `receipt-detail.html` | Individual receipt view |
| `receipt-uploads.html` | Upload interface with drag-drop |
| `receipt-search.html` | Search functionality |
| `receipt-overview.html` | Period-based overview |
| `receipt-errors.html` | Error display |
| `statistics-*.html` (7 templates) | Various statistics views |
| `fragments/receipt-list.html` | Reusable table fragments |

**Total: ~23 templates, 1 reusable fragment file**

### 4.3 Responsive Design Assessment

**Current state: Desktop-first with responsive fallbacks**

The UI uses Bootstrap 5's grid system with breakpoints at `992px` (lg) and `576px` (sm). Mobile-specific adaptations include:
- Hamburger menu navigation
- Responsive table scrolling (`.table-responsive`)
- Column stacking on mobile (`.col-md-6`, `.col-lg-4`)
- Some `d-none d-lg-flex` visibility toggles

**Issues:**
- **Tables are problematic on mobile** – Receipt tables with many columns require horizontal scrolling; no card-based alternative for mobile
- **Dense data displays** – Statistics pages pack a lot of numbers into tables that are hard to read on small screens
- **No mobile-specific touch interactions** – Drag-drop upload doesn't have a mobile-friendly alternative (though file input fallback exists)

### 4.4 Component Reuse

**Existing reusable fragments:**
- `receipt-list.html` provides `simpleReceiptTable()` and `receiptTable()` fragments

**Duplicated patterns that should be extracted to fragments:**

1. **Scope toggle** (admin/user view switch) – duplicated across 5+ templates:
   ```html
   <div class="btn-group btn-group-sm" role="group">
     <a class="btn btn-outline-primary">Mina kvitton</a>
     <a class="btn btn-outline-primary">Alla kvitton</a>
   </div>
   ```

2. **Alert/notification blocks** – success/error/warning patterns repeated in most templates

3. **Statistics page headers** – breadcrumb + heading pattern repeated across all statistics templates

4. **Status badges** – reconciliation/upload status badges using similar pattern

**Recommendation:** Extract these into Thymeleaf fragments:
- `fragments/scope-toggle.html` – Admin/user scope selection
- `fragments/alerts.html` – Standardised alert blocks
- `fragments/breadcrumb.html` – Breadcrumb navigation
- `fragments/status-badge.html` – Status badge variants

### 4.5 Should You Keep Thymeleaf?

This is the key architectural question. Here is an honest assessment of each option:

#### Option A: Keep Thymeleaf (Recommended for now)

| Pros | Cons |
|------|------|
| Zero client-side framework complexity | Page reloads for every navigation |
| Server-side rendering is fast and SEO-friendly | Limited interactivity without writing vanilla JS |
| Spring integration is excellent | Harder to build rich, stateful UIs |
| Already working and well-understood | Template duplication grows over time |
| Low learning curve for the current stack | Testing templates requires integration tests |

**Verdict:** Thymeleaf is the right choice **if the UI stays primarily read-heavy** (viewing receipts, statistics, dashboards). The current feature set is a good fit.

#### Option B: Add HTMX (Best incremental upgrade)

| Pros | Cons |
|------|------|
| Keep all existing templates | Another dependency to manage |
| Add SPA-like interactions without JS framework | Learning HTMX patterns |
| Partial page updates (no full reloads) | Server-side still renders HTML |
| Works perfectly with Thymeleaf | Can lead to complex server endpoints |
| Very small library (~14KB) | Less ecosystem/tooling than React |

**Verdict:** HTMX would be the **ideal next step** for this project. It solves the biggest Thymeleaf weakness (full page reloads) while keeping the server-rendered architecture. Specific wins:
- Replace polling in `receipts.js` with `hx-trigger="every 2s"`
- Replace full page reloads for scope toggles and filters with `hx-swap`
- Add inline editing for categories and tags
- Partial updates for statistics dashboards

#### Option C: Switch to React

| Pros | Cons |
|------|------|
| Rich interactive UI | Massive migration effort |
| Huge ecosystem and tooling | Need a separate API layer (REST/GraphQL) |
| Component reuse is natural | Build complexity increases significantly |
| Good for complex, stateful views | Overkill for a receipt archive |
| Better testing with React Testing Library | Doubles the tech stack |

**Verdict:** A React frontend is **overkill for this project** unless the goal is specifically to learn React. The receipt archive is a read-heavy CRUD app that does not need a single-page application. If React is chosen as a learning goal, consider a hybrid approach: keep Thymeleaf for the landing pages and use React for the receipt workspace (dashboard, uploads, statistics).

#### Recommendation

1. **Short term:** Stay with Thymeleaf, extract duplicate patterns into fragments
2. **Medium term:** Add HTMX for interactive enhancements (replace polling, add inline editing)
3. **Long term (optional):** If learning React is a goal, build a separate `frontend/` module with React for the dashboard only

### 4.6 UI Improvement Suggestions

1. **Mobile card view for receipts** – On screens below 768px, render receipts as cards instead of tables
2. **Better loading states** – Replace the splash screen with skeleton loading patterns
3. **Toasts for actions** – Use Bootstrap toasts instead of full alert blocks for transient feedback
4. **Dark mode** – Bootstrap 5.3+ supports `data-bs-theme="dark"` natively
5. **Better data visualisation** – The statistics pages could use more charts (Chart.js is already included)
6. **Accessibility** – Add ARIA labels to interactive elements; ensure keyboard navigation works throughout

---

## 5. Data Storage Review

### 5.1 Firestore Collection Structure

| Collection | Purpose | Ownership | Index Requirements |
|-----------|---------|-----------|-------------------|
| `users` | User profiles and roles | Web service | By email (unique) |
| `receiptExtractions` | Parsed receipt metadata | Receipt parser | Composite: owner+date, owner+store, owner+createdAt |
| `receiptItems` | Individual line items | Receipt parser | By EAN, by name |
| `receiptItemStats` | Aggregated item statistics | Receipt parser | By item name |
| `categories` | User-defined categories | Web service | None special |
| `item_categories` | Item-to-category mappings | Web service | By item/EAN |
| `item_tags` | Item-to-tag mappings | Web service | By item/EAN |
| `tagSummaries` | Cached tag statistics per user | Web service | By user |
| `tagSummaryMeta` | Tag summary metadata | Web service | None |

### 5.2 Assessment

**Good decisions:**
- Separate collections for different entity types (proper NoSQL modelling)
- Composite indexes defined for common query patterns
- Configurable collection names via properties
- Firestore read tracking for cost monitoring (excellent cost awareness)

**Concerns:**

1. **Relational queries on NoSQL** – Filtering receipts by date range + owner + store requires composite indexes. Each new filter combination needs a new index. A relational database (Cloud SQL for PostgreSQL) would handle ad-hoc queries more naturally.

2. **No subcollection usage** – All data is in top-level collections. Receipt items could be subcollections under receipts, reducing query scope and improving performance.

3. **Denormalization gaps** – Store names and item names appear to be stored as strings without normalisation. If a store name changes, all historical receipts need updating.

4. **No TTL or lifecycle policies** – Old receipt data and tag summaries grow indefinitely. Consider Firestore TTL policies for temporary data or archival strategies for old receipts.

5. **Backup strategy is manual** – The dashboard provides admin-triggered backup/restore, but there is no automated scheduled backup. Consider Cloud Scheduler + Cloud Functions for daily exports.

### 5.3 Recommendation

For a learning project, **Firestore is fine** – it teaches NoSQL patterns, index management, and GCP integration. For a production receipt archive, consider:
- **Cloud SQL for PostgreSQL** for the structured receipt data (relational queries, ACID transactions)
- **Keep Firestore** for user profiles and real-time features (it excels at these)
- **Keep Cloud Storage** for receipt file storage (correct choice)

> **Learning note:** The current Firestore usage is valuable for understanding NoSQL trade-offs. The composite indexes and read tracking are evidence of good learning outcomes.

---

## 6. CI/CD Pipeline Review

### 6.1 Current Workflows

| Workflow | Trigger | Jobs | Duration |
|----------|---------|------|----------|
| `pr-validation.yml` | PRs to `main` | 6 parallel: test-core, test-web, test-receipt-parser, verify, docker-build-web, docker-build-receipt-parser | ~8-12 min |
| `deploy-cloud-run.yml` | Manual dispatch | Build + push + deploy + cleanup | ~15-20 min |
| `release-and-deploy.yml` | Manual dispatch | Version bump + tag + build + deploy | ~20-25 min |

### 6.2 Assessment

**Strengths:**
- **Parallel test execution** – All three modules test simultaneously
- **Docker build validation** – PRs verify both images build successfully
- **Spring Modulith verification** – Module boundaries are checked in CI
- **Test artifacts uploaded** – Test results are available for review
- **Clean deployment scripts** – Terraform automation with proper state management

**Issues:**

1. **No frontend linting in CI** – ESLint and Stylelint are configured but not run in `pr-validation.yml`
   
   **Fix:** Add a `lint-frontend` job:
   ```yaml
   lint-frontend:
     runs-on: ubuntu-latest
     steps:
       - uses: actions/checkout@v4
       - uses: actions/setup-node@v4
         with:
           node-version: '20'
       - run: cd web && npm ci && npm run lint
   ```

2. **No test coverage reporting** – No JaCoCo or similar tool to measure and track coverage over time
   
   **Fix:** Add JaCoCo Maven plugin and publish coverage reports in CI.

3. **No smoke tests after deployment** – The deploy workflow pushes to Cloud Run but never verifies the services are healthy
   
   **Fix:** Add a post-deployment health check step:
   ```bash
   curl -f https://pklnd-web-<hash>.run.app/actuator/health/readiness
   ```

4. **No automated deployment** – Both deployment workflows require manual dispatch. Consider auto-deploying to a test environment on merge to `main`.

5. **Missing dependency caching** – Maven dependencies are downloaded fresh on every run. Add GitHub Actions caching for `.m2/repository`.

6. **No Vite build in CI** – The Vite asset pipeline is not executed during PR validation. Template tests pass but production assets may be broken.

### 6.3 Recommended CI/CD Improvements

| Priority | Improvement | Effort |
|----------|------------|--------|
| 🔴 High | Add frontend linting to PR validation | 30 min |
| 🔴 High | Add JaCoCo test coverage reporting | 2 hours |
| 🟡 Medium | Add Maven dependency caching | 30 min |
| 🟡 Medium | Add Vite build step to PR validation | 30 min |
| 🟡 Medium | Auto-deploy to test environment on merge | 2 hours |
| 🟢 Low | Add post-deployment smoke tests | 1 hour |
| 🟢 Low | Add dependency vulnerability scanning (Dependabot/Renovate) | 1 hour |

---

## 7. Testing Review

### 7.1 Test Inventory

| Module | Test Classes | Type |
|--------|-------------|------|
| **Core** | 2 | Unit (GCS service) + modularity |
| **Web** | 27 | Unit + integration (controllers, services, config, modularity) |
| **Receipt Parser** | 13 | Unit + integration (parsing, API, regression, modularity) |
| **Frontend** | 0 | ❌ No JavaScript tests |
| **E2E** | 0 | ❌ No end-to-end tests |
| **Total** | 42 | |

### 7.2 Testing Frameworks

| Framework | Used For |
|-----------|----------|
| JUnit 5 | All test classes |
| Mockito | GCP service mocking |
| AssertJ | Fluent assertions |
| Spring Test / MockMvc | Controller integration tests |
| Spring Security Test | `@WithMockUser` for auth tests |
| Spring Modulith Test | Module boundary verification |

### 7.3 Coverage Assessment

**Well-tested areas:**
- ✅ Controller endpoints (routing, auth, response codes)
- ✅ Firestore read tracking (unit tests)
- ✅ Receipt parsing (unit + regression tests with real PDFs)
- ✅ GCS storage service (mocked GCS API)
- ✅ Modularity boundaries (Spring Modulith)
- ✅ Security redirects and configuration

**Undertested areas:**
- ❌ **Frontend JavaScript** – No tests for table sorting, polling, drag-drop upload, chart rendering
- ❌ **Thymeleaf template rendering** – No tests verify that templates render correctly with model data
- ❌ **End-to-end user journeys** – No tests for login → upload → view receipt flow
- ❌ **Error handling paths** – Many catch blocks return defaults without test verification
- ❌ **Categorisation/tagging** – Limited tests for the category and tag assignment workflows
- ❌ **Statistics calculations** – `DashboardStatisticsService` has tests but coverage of edge cases is unclear

### 7.4 Key Testing Gaps

1. **No test coverage metrics** – Cannot quantify what percentage of code is covered. JaCoCo is not configured.

2. **No UI testing at all** – The 23 Thymeleaf templates and 5 JavaScript bundles have zero automated tests. For a web application, this is a significant gap.

3. **No contract tests** – The web service calls the receipt-parser service via REST. There are no tests verifying the contract between them (e.g. Spring Cloud Contract or Pact).

4. **No Firestore integration tests** – All Firestore interactions are mocked. While this avoids GCP costs, it means the actual Firestore query logic is untested. Consider using the Firestore emulator in CI.

### 7.5 Recommended Testing Improvements

| Priority | Improvement | Effort | Impact |
|----------|------------|--------|--------|
| 🔴 High | Add JaCoCo for coverage metrics | 2h | Enables tracking coverage over time |
| 🔴 High | Add Thymeleaf template rendering tests | 4h | Catches broken templates before deployment |
| 🟡 Medium | Add Playwright/Cypress E2E tests for critical paths | 1-2 days | Catches integration issues |
| 🟡 Medium | Add Vitest for JavaScript module tests | 4h | Tests client-side logic (sorting, upload) |
| 🟡 Medium | Add Firestore emulator integration tests | 4h | Tests actual query behaviour |
| 🟢 Low | Add contract tests for web↔receipt-parser | 1 day | Prevents API drift between services |
| 🟢 Low | Add load testing with k6 or Gatling | 1 day | Establishes performance baselines |

---

## 8. Security Review

### 8.1 Current Security Posture

**Strengths:**
- ✅ CSP nonces via `CspNonceFilter` (prevents inline script injection)
- ✅ HSTS with 1-year max-age, includeSubDomains, preload
- ✅ X-Frame-Options DENY (prevents clickjacking)
- ✅ Referrer-Policy SAME-ORIGIN
- ✅ CSRF protection on all forms
- ✅ Password encoding with Spring Security's `PasswordEncoder`
- ✅ OAuth2 with Google (delegated authentication)
- ✅ Signed URLs for file uploads (prevents direct bucket access)
- ✅ Minimal IAM roles per service account
- ✅ Secret Manager for credential storage

**Concerns:**

1. **`/api/billing/alerts` is publicly accessible** – This endpoint accepts Pub/Sub messages without token verification. A malicious actor could send fake billing alerts to trigger a shutdown.
   
   **Recommendation:** Validate the Pub/Sub push subscription token or verify the request origin.

2. **No rate limiting** – Login, registration, and search endpoints have no rate limiting. A brute-force or denial-of-service attack could succeed.
   
   **Recommendation:** Add Spring Security rate limiting or use Cloud Armor.

3. **No input sanitisation on search** – Receipt search terms appear to be passed directly to Firestore queries. While Firestore handles injection differently than SQL, validate and sanitise user input.

4. **Docker images not scanned** – Container images are pushed to Artifact Registry without vulnerability scanning.
   
   **Recommendation:** Enable Artifact Registry's built-in vulnerability scanning.

---

## 9. Cost & Operational Review

### 9.1 Current Cost Controls

| Mechanism | Status | Assessment |
|-----------|--------|------------|
| Scale-to-zero (min instances: 0) | ✅ Active | Eliminates idle compute costs |
| Budget alerts at 50%, 75%, 90%, 100% | ✅ Active | Good visibility |
| Auto-shutdown at 100% budget | ✅ Active | Excellent cost protection |
| Image cleanup (keep last 3) | ✅ Active | Reduces storage costs |
| Firestore read tracking | ✅ Active | Cost awareness |

### 9.2 Operational Concerns

1. **Cold start latency** – With `min_instance_count = 0`, the first request after idle time takes 10-15 seconds. Consider setting `min_instance_count = 1` for the web service in production (~$0.40/month).

2. **No automated backups** – Firestore backups require manual admin action. A single Cloud Scheduler job could automate daily exports.

3. **No alerting on errors** – There is no Cloud Monitoring alerting for application errors, latency spikes, or failed deployments.

4. **No log-based metrics** – Application logs go to Cloud Logging but no custom metrics are extracted. Consider structured logging with metric extraction for key operations (receipt uploads, parsing, login).

> **Learning note:** Cloud Monitoring, Cloud Scheduler, and structured logging are excellent learning targets for future iterations. They add operational maturity without significant cost (Monitoring has a generous free tier).

---

## 10. Improvement Roadmap

### Phase 1: Quick Wins (1-2 weeks)

- [ ] **Add JaCoCo** – Configure Maven JaCoCo plugin, add coverage reporting to CI
- [ ] **Add frontend linting to CI** – Wire ESLint and Stylelint into `pr-validation.yml`
- [ ] **Add Vite build to CI** – Ensure production assets are verified in PRs
- [ ] **Extract Thymeleaf fragments** – Create reusable fragments for scope toggles, alerts, breadcrumbs, status badges
- [ ] **Add Maven dependency caching** to GitHub Actions
- [ ] **Validate Pub/Sub tokens** on `/api/billing/alerts`

### Phase 2: Code Quality (2-4 weeks)

- [ ] **Split `ReceiptController`** into `ReceiptUploadController`, `ReceiptSearchController`, and move business logic to services
- [ ] **Split `ReceiptExtractionService`** into `ReceiptRepository`, `ReceiptSearchService`, `ReceiptItemService`
- [ ] **Split `FirestoreUserService`** into repository and service layers
- [ ] **Add Thymeleaf template rendering tests** for all pages
- [ ] **Add Vitest** for JavaScript module tests (table sorting, upload, polling)
- [ ] **Standardise exception handling** – Replace catch-and-swallow with proper error propagation

### Phase 3: CI/CD Maturity (2-4 weeks)

- [ ] **Add post-deployment smoke tests** – Health check and basic functionality verification
- [ ] **Auto-deploy to test environment** on merge to `main`
- [ ] **Add Dependabot or Renovate** for automated dependency updates
- [ ] **Add Artifact Registry vulnerability scanning**
- [ ] **Add contract tests** between web and receipt-parser services

### Phase 4: UI Enhancement (4-8 weeks)

- [ ] **Add HTMX** for progressive enhancement (replace polling, add partial page updates)
- [ ] **Mobile card view** for receipt lists on small screens
- [ ] **Dark mode** using Bootstrap 5.3 `data-bs-theme`
- [ ] **Skeleton loading** instead of splash screen
- [ ] **More Chart.js visualisations** on statistics pages
- [ ] **Accessibility audit** – ARIA labels, keyboard navigation, screen reader testing

### Phase 5: Operational Maturity (4-8 weeks)

- [ ] **Automated Firestore backups** via Cloud Scheduler
- [ ] **Cloud Monitoring alerts** for errors, latency, and failed deployments
- [ ] **Structured logging** with custom metric extraction
- [ ] **Playwright E2E tests** for critical user journeys
- [ ] **Set `min_instance_count = 1`** for web service in production
- [ ] **Rate limiting** on authentication and search endpoints

### Phase 6: Optional Architecture Evolution (8+ weeks)

- [ ] **Evaluate Cloud SQL** for receipt data (if relational queries become a pain point)
- [ ] **Firestore subcollections** for receipt items (if staying with Firestore)
- [ ] **React frontend** for dashboard only (if learning React is a goal)
- [ ] **WebSocket/SSE** for real-time receipt processing status (if HTMX is not sufficient)
- [ ] **Multi-region deployment** (if availability requirements increase)

---

## Appendix: Files and Classes Referenced

### Web Module – Key Files

| File | Lines | Assessment |
|------|-------|------------|
| `ReceiptController.java` | ~2,120 | 🔴 Needs splitting |
| `ReceiptExtractionService.java` | ~851 | 🔴 Needs splitting |
| `FirestoreUserService.java` | ~646 | 🟡 Consider splitting |
| `SecurityConfig.java` | ~100 | ✅ Clean and well-structured |
| `CurrentUserAdvice.java` | ~150 | ✅ Good use of @ControllerAdvice |
| `DashboardStatisticsService.java` | ~300 | ✅ Reasonable size |

### Receipt Parser Module – Key Files

| File | Lines | Assessment |
|------|-------|------------|
| `HybridReceiptExtractor.java` | ~200 | ✅ Clean strategy pattern |
| `ReceiptParsingHandler.java` | ~150 | ✅ Good orchestration |
| `AIReceiptExtractor.java` | ~200 | ✅ Clean AI integration |
| `LegacyPdfReceiptExtractor.java` | ~150 | ✅ Appropriate for legacy support |

### Infrastructure – Key Files

| File | Purpose | Assessment |
|------|---------|------------|
| `infra/terraform/infrastructure/main.tf` | Core GCP resources | ✅ Well-structured |
| `infra/terraform/deployment/main.tf` | Cloud Run services | ✅ Clean separation |
| `Dockerfile` (root) | Web service image | ✅ Good multi-stage build |
| `receipt-parser/Dockerfile` | Parser service image | ✅ Good multi-stage build |
| `.github/workflows/pr-validation.yml` | PR checks | 🟡 Missing frontend linting |
| `.github/workflows/deploy-cloud-run.yml` | Deployment | 🟡 Missing smoke tests |
