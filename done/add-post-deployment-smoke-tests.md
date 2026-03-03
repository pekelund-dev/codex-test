# Add post-deployment smoke tests

**Labels:** ci/cd, testing

**Description**
The deploy workflow pushes to Cloud Run but never verifies the services are healthy.
Add a post-deployment health check and basic functionality verification.

**Tasks**
- [x] Add smoke test step to `deploy-cloud-run.yml` that curls the health endpoint
- [x] Verify the login page renders (HTTP 200 on `/login`)
- [x] Fail the workflow if smoke tests fail

**Acceptance criteria**
- [x] Deployment workflow fails if service is unhealthy after deploy
- [x] Smoke test results visible in workflow logs (GitHub Step Summary)
