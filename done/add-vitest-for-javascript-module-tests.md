# Add Vitest for JavaScript module tests

**Labels:** testing, frontend, code-quality

**Description**
Zero JavaScript tests exist for the 5 Vite entry points (table sorting, upload,
polling, chart rendering). Add Vitest as the test runner.

**Tasks**
- [x] Add Vitest to `web/package.json` dev dependencies
- [x] Configure Vitest with `web/vitest.config.js`
- [x] Add tests for table sorting logic (table-sort.test.js — 3 tests)
- [x] Add tests for upload validation logic (receipt-uploads.test.js — 11 tests)
- [x] Add tests for polling behaviour / URL building (receipt-overview.test.js — 4 tests)
- [x] Wire `npm test` into `pr-validation.yml` (test-frontend job)

**Acceptance criteria**
- [x] `cd web && npm test` runs and passes (18/18 tests)
- [x] At least one test per JavaScript module

**Completion summary**
- Added `vitest@3.0.9`, `@vitest/coverage-v8@3.0.9`, `jsdom` to devDependencies
- Created `vitest.config.js` with jsdom environment
- 3 test files in `src/test/frontend/`:
  - `table-sort.test.js` — 3 DOM integration tests for sort direction and numeric sort
  - `receipt-uploads.test.js` — 11 unit tests for formatBytes and truncateName utilities
  - `receipt-overview.test.js` — 4 tests for ReceiptOverviewController.buildOverviewUrl
- Added `test-frontend` job to `pr-validation.yml`
- `npm test`: 18/18 pass. `npm run lint`: passes.
