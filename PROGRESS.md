# Split ReceiptExtractionService into repository and service layers

**Labels:** code-quality, refactoring

**Description**
`ReceiptExtractionService` combines CRUD, search, item extraction, and
EAN deduplication in a single 851-line class.

**Tasks**
- [ ] Extract `ReceiptRepository` — pure CRUD against Firestore
- [ ] Extract `ReceiptSearchService` — search and filtering logic
- [ ] Extract `ReceiptItemService` — item-level operations and EAN management
- [ ] Update all callers and tests

**Acceptance criteria**
- No single class exceeds ~300 lines
- Existing functionality and tests preserved

**References**
- docs/architecture-review.md § 3.2 — Issue 2
