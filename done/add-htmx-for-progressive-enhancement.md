# Add HTMX for progressive enhancement

**Tasks**
- [x] Add HTMX CDN to layout.html (v2.0.4 from unpkg)
- [x] Create HTMX fragment endpoint GET /receipts/uploads/files-fragment
- [x] Extract receipt files table to fragments/receipt-files-table.html
- [x] Add hx-get + hx-trigger="every 5s" on #receipt-files-dashboard in receipt-uploads.html
- [x] Fallback works - initial page render uses th:replace to show content without HTMX

**Completion**: 79 Java tests pass
