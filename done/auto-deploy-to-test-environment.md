# Add auto-deploy staging workflow

**Labels:** ci/cd

**Description**
Both deployment workflows require manual dispatch. Add an automatic deployment
to a test/staging environment on merge to `main`.

**Tasks**
- [x] Use existing test Cloud Run services (`pklnd-web-test`, `pklnd-receipts-test`)
- [x] Add workflow `auto-deploy-staging.yml` triggered on push to `main`
- [x] Include smoke tests (reused from deploy-cloud-run.yml pattern)

**Acceptance criteria**
- [x] Merging to `main` triggers a deployment to the test environment
- [x] Deployment includes health verification
