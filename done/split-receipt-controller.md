# Split ReceiptController into focused controllers and services

**Labels:** code-quality, refactoring

**Description**
`ReceiptController` is 2,120 lines and handles uploads, search, statistics,
price calculations, and more. Split it into focused controllers with dedicated services.

**Tasks**
- [x] Create `ReceiptUploadController` — upload handling (`GET /receipts/uploads`, `POST /receipts/upload`)
- [x] Create `ReceiptSearchController` — search (`GET /receipts/search`)
- [x] Create `ReceiptOverviewController` — overview and period data (`GET /receipts/overview`, `GET /receipts/overview/data`)
- [x] Create `ReceiptItemController` — item purchases (`GET /receipts/items/{eanCode}`)
- [x] Create `ReceiptScopeHelper` @Component — shared scope/admin utilities
- [x] Move `ReceiptViewScope` enum to dedicated top-level file
- [x] Update tests for the new controllers/services

**Acceptance criteria**
- [x] All existing routes still resolve with identical behaviour
- [x] ReceiptController reduced from 2,120 to 723 lines; no single new controller exceeds ~300 lines
- [x] All 74 existing tests pass

**References**
- docs/architecture-review.md § 3.2 — Issue 1
- web/src/main/java/dev/pekelund/pklnd/web/ReceiptController.java
