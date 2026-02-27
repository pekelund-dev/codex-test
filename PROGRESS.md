# Extract reusable Thymeleaf fragments (scope toggle, alerts, breadcrumbs, badges)

**Labels:** ui, code-quality, quick-win

**Description**
Several UI patterns are duplicated across 5+ templates. Extract them into shared
Thymeleaf fragments to reduce duplication and improve consistency.

**Tasks**
- [ ] Create `fragments/scope-toggle.html` — admin/user scope selection
- [ ] Create `fragments/alerts.html` — standardised success/error/warning blocks
- [ ] Create `fragments/breadcrumb.html` — breadcrumb navigation
- [ ] Create `fragments/status-badge.html` — reconciliation/upload status badges
- [ ] Update all templates to use the new fragments

**Acceptance criteria**
- No duplicate scope toggle, alert, breadcrumb, or badge HTML remains in templates
- UI behaviour unchanged
- Existing tests still pass

**References**
- docs/architecture-review.md § 4.4 — Component Reuse
