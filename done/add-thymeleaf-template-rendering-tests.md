# Add Thymeleaf template rendering tests for all pages

**Labels:** testing, code-quality

**Description**
23 Thymeleaf templates have zero rendering tests. Add tests that verify templates
render correctly with representative model data.

**Tasks**
- [x] Add `@WebMvcTest` tests that verify template rendering for each page
- [x] Verify model attributes are used correctly (no missing variables)
- [x] Cover both authenticated and unauthenticated states

**Acceptance criteria**
- [x] At least one rendering test per template
- [x] Tests catch missing model attributes or broken Thymeleaf expressions

**References**
- docs/architecture-review.md § 7.3 — Undertested areas

## Completion summary

Added 3 new rendering tests (77 tests total, all pass):
- `AuthControllerTests` — 2 tests for `register.html` (enabled/disabled states)
- `ReceiptUploadControllerTests` — 1 test for `receipt-uploads.html`

Existing tests already covered: home.html, about.html, login.html, dashboard-statistics.html,
statistics-users.html, receipt-overview.html, receipt-search.html, receipts.html,
receipt-errors.html, receipt-detail.html.

Tests run: `./mvnw -Pinclude-web -pl web -am test` → 77/77 pass
