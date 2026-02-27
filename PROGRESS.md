# Add frontend linting to PR validation

**Labels:** ci/cd, frontend, quick-win

**Description**
ESLint and Stylelint are configured in `web/` but are not run during PR validation.
Add a `lint-frontend` job to `pr-validation.yml`.

**Tasks**
- [ ] Add a `lint-frontend` job to `.github/workflows/pr-validation.yml`
- [ ] Run `cd web && npm ci && npm run lint` in CI
- [ ] Ensure the job blocks merging on lint failures

**Acceptance criteria**
- PR validation fails if ESLint or Stylelint reports errors
- No new lint errors on the current codebase

**References**
- docs/architecture-review.md § 6.2 — CI/CD issues, item 1
