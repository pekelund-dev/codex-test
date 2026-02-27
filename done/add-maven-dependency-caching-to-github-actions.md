# Add Maven dependency caching to GitHub Actions

**Labels:** ci/cd, quick-win

**Description**
Maven dependencies are downloaded fresh on every CI run. Add caching using
`actions/cache` or the built-in `setup-java` cache option.

**Tasks**
- [x] Add Maven `.m2/repository` caching to `pr-validation.yml` (already present via `cache: 'maven'` in setup-java)
- [x] Add caching to `release-and-deploy.yml` (added `cache: 'maven'` to setup-java step)
- [x] `deploy-cloud-run.yml` — Maven runs inside Docker build (layer caching already configured via `cache-from: type=gha`)

**Acceptance criteria**
- [x] Second CI run is measurably faster (cache hit in logs)

**References**
- docs/architecture-review.md § 6.2 — CI/CD issues, item 5
