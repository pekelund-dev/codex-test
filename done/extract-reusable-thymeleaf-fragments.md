# Extract reusable Thymeleaf fragments (scope toggle, alerts, breadcrumbs, badges)

**Labels:** ui, code-quality, quick-win

**Description**
Several UI patterns are duplicated across 5+ templates. Extract them into shared
Thymeleaf fragments to reduce duplication and improve consistency.

**Tasks**
- [x] Create `fragments/scope-toggle.html` — admin/user scope selection
- [x] Create `fragments/alerts.html` — standardised success/error/warning blocks
- [x] Create `fragments/breadcrumb.html` — breadcrumb navigation
- [x] Create `fragments/status-badge.html` — reconciliation/upload status badges
- [x] Update all templates to use the new fragments

**Acceptance criteria**
- [x] No duplicate scope toggle, alert, breadcrumb, or badge HTML remains in templates
- [x] UI behaviour unchanged
- [x] Existing tests still pass (71/71)

**References**
- docs/architecture-review.md § 4.4 — Component Reuse
