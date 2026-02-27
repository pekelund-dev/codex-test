# Evaluation: React for Dashboard Module

**Date:** 2026-02-27  
**Status:** Recommendation — Defer React adoption; proceed with HTMX

---

## Current State

Frontend is Thymeleaf MPA + vanilla JavaScript via Vite (5 entry points).
Bootstrap 5.3.3 from CDN. No SPA framework.

## React Hybrid Approach Analysis

### Advantages
- Rich interactive dashboard with real-time updates
- Ecosystem (React Query, charts, data grids)
- Learning opportunity for modern frontend development

### Disadvantages
- **Complexity**: Two build pipelines (Maven + Node), two rendering models (SSR + CSR)
- **API layer**: Requires REST endpoints for all dashboard data (significant new work)
- **SEO/initial load**: CSR has first-load latency vs server-rendered Thymeleaf
- **Auth**: CSRF token strategy differs between Thymeleaf and React SPA

### HTMX Alternative (already implemented)

HTMX v2.0.4 is now integrated for progressive enhancement:
- `POST /receipts/uploads/files-fragment` returns HTML fragments
- `hx-trigger="every 5s"` for polling without custom JS
- Works as progressive enhancement over server-rendered HTML

## Recommendation

**Defer React adoption** and expand HTMX usage instead:
1. HTMX provides 80% of the interactivity benefit with 20% of the complexity.
2. No new API layer required — server returns HTML fragments.
3. Existing Thymeleaf templates continue to work for non-JS clients.

**Revisit React** if:
- A dedicated frontend developer joins the team
- The dashboard requires complex state management (multi-step flows, optimistic updates)
- Mobile app sharing of the API becomes a goal
