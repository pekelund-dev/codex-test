# Add Thymeleaf template rendering tests for all pages

**Labels:** testing, code-quality

**Description**
23 Thymeleaf templates have zero rendering tests. Add tests that verify templates
render correctly with representative model data.

**Tasks**
- [ ] Add `@WebMvcTest` tests that verify template rendering for each page
- [ ] Verify model attributes are used correctly (no missing variables)
- [ ] Cover both authenticated and unauthenticated states

**Acceptance criteria**
- At least one rendering test per template
- Tests catch missing model attributes or broken Thymeleaf expressions

**References**
- docs/architecture-review.md § 7.3 — Undertested areas

## Implementation plan

Templates already covered by existing tests:
- `home.html` — HomeControllerTests
- `about.html` — HomeControllerTests
- `login.html` — LoginControllerTests
- `dashboard-statistics.html` — StatisticsControllerTests (GET /dashboard)
- `statistics-users.html` — StatisticsControllerTests
- `receipt-overview.html` — ReceiptOverviewPageTests
- `receipt-search.html` — ReceiptControllerSearchTests
- `receipts.html` / `receipt-errors.html` — ReceiptControllerRouteTests
- `receipt-detail.html` — ReceiptControllerReceiptViewTests

Templates needing new tests:
1. `register.html` — AuthController → AuthControllerTests
2. `receipt-uploads.html` — ReceiptUploadController → ReceiptUploadControllerTests
3. `receipt-item.html` — ReceiptItemController (already partially in ReceiptControllerItemViewTests)

Steps:
1. Create `AuthControllerTests.java` — tests GET /register renders register.html
2. Create `ReceiptUploadControllerTests.java` — tests GET /receipts/uploads renders receipt-uploads.html
3. Verify ReceiptControllerItemViewTests already covers receipt-item.html
