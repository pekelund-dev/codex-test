# Add Playwright E2E tests for critical user journeys

**Status:** Completed

- [x] e2e/ directory created with package.json and playwright.config.js
- [x] e2e/tests/critical-journeys.test.js:
  - Login page renders with form
  - Login page shows error on invalid credentials
  - Unauthenticated users redirected from /dashboard, /receipts, /receipts/uploads
  - Register page renders
- [x] Playwright E2E tests wired into auto-deploy-staging.yml
- Run: `cd e2e && npm ci && npx playwright install chromium && E2E_BASE_URL=<url> npx playwright test`
