# Add Vitest for JavaScript module tests

**Labels:** testing, frontend, code-quality

**Description**
Zero JavaScript tests exist for the 5 Vite entry points (table sorting, upload,
polling, chart rendering). Add Vitest as the test runner.

**Tasks**
- [ ] Add Vitest to `web/package.json` dev dependencies
- [ ] Configure Vitest with `web/vitest.config.js`
- [ ] Add tests for table sorting logic
- [ ] Add tests for upload validation logic
- [ ] Add tests for polling behaviour
- [ ] Wire `npm test` into `pr-validation.yml`

**Acceptance criteria**
- `cd web && npm test` runs and passes
- At least one test per JavaScript module

**References**
- docs/architecture-review.md § 7.4 — Key Testing Gaps, item 2

## Implementation plan

1. Install vitest as devDependency: `npm install --save-dev vitest`
2. Create `vitest.config.js` with jsdom environment
3. Add `"test": "vitest run"` script to `package.json`
4. Create test files in `src/test/frontend/`:
   - `table-sort.test.js` - DOM integration test: set up table, fire DOMContentLoaded, verify sort  
   - `receipt-uploads.test.js` - test formatBytes/truncateName pure functions (DOM setup)
   - `receipt-overview.test.js` - test URL building (ReceiptOverviewController.buildOverviewUrl)
5. Wire `npm test` into `pr-validation.yml`
