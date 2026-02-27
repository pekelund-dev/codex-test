# Split ReceiptController into focused controllers and services

**Labels:** code-quality, refactoring

**Description**
`ReceiptController` is 2,120 lines and handles uploads, search, statistics,
price calculations, and more. Split it into focused controllers with dedicated services.

**Tasks**
- [ ] Create `ReceiptUploadController` — upload handling, file validation
- [ ] Create `ReceiptSearchController` — search and filtering
- [ ] Create `ReceiptOverviewController` — overview and statistics display
- [ ] Extract `ReceiptStatisticsService` — computation logic
- [ ] Extract `ReceiptPriceService` — price calculations and formatting
- [ ] Move inline enum types to dedicated files
- [ ] Update tests for the new controllers/services

**Acceptance criteria**
- All existing routes still resolve with identical behaviour
- No single controller exceeds ~300 lines
- All existing tests pass; new tests cover the new classes

**References**
- docs/architecture-review.md § 3.2 — Issue 1
- web/src/main/java/dev/pekelund/pklnd/web/ReceiptController.java
