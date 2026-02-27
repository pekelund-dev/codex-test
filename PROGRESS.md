# Add Maven dependency caching to GitHub Actions

**Labels:** ci/cd, quick-win

**Description**
Maven dependencies are downloaded fresh on every CI run. Add caching using
`actions/cache` or the built-in `setup-java` cache option.

**Tasks**
- [ ] Add Maven `.m2/repository` caching to `pr-validation.yml`
- [ ] Add caching to `deploy-cloud-run.yml` and `release-and-deploy.yml`

**Acceptance criteria**
- Second CI run is measurably faster (cache hit in logs)

**References**
- docs/architecture-review.md § 6.2 — CI/CD issues, item 5
