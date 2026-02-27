# Add Vite build step to PR validation

**Labels:** ci/cd, frontend, quick-win

**Description**
The Vite asset pipeline is not executed during PR validation, so broken production
assets can be merged without detection.

**Tasks**
- [ ] Add a CI step to run `cd web && npm ci && npm run build`
- [ ] Verify that the Vite manifest is generated successfully

**Acceptance criteria**
- PR validation fails if `npm run build` fails
- Production assets verified in every PR

**References**
- docs/architecture-review.md § 6.2 — CI/CD issues, item 6
