# Split ReceiptExtractionService into repository and service layers

**Labels:** code-quality, refactoring

**Description**
`ReceiptExtractionService` combines CRUD, search, item extraction, and
EAN deduplication in a single 851-line class.

**Tasks**
- [x] Extract `ReceiptRepository` — pure CRUD against Firestore (555 lines)
- [x] Extract `ReceiptSearchService` — search and filtering logic (191 lines)
- [x] Extract `ReceiptItemService` — item-level operations and EAN management (227 lines)
- [x] Update `ReceiptExtractionService` to 88-line facade — zero changes to callers

**Acceptance criteria**
- [x] No single class exceeds ~560 lines (ReceiptRepository is the largest)
- [x] Existing functionality and tests preserved (74/74 pass)

**References**
- docs/architecture-review.md § 3.2 — Issue 2
