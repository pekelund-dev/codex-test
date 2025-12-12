# GitHub Actions CI/CD Pipeline Guide

This document provides a comprehensive guide for setting up a CI/CD pipeline using GitHub Actions as an alternative to Google Cloud Build.

## Overview

Currently, the project uses Google Cloud Build for building and deploying Docker containers. This guide explores migrating to GitHub Actions for CI/CD while maintaining the ability to deploy to Google Cloud Run.

## Cost Comparison

### Current Setup: Google Cloud Build

| Aspect | Details |
|--------|---------|
| **Machine Type** | E2_HIGHCPU_8 (8 vCPUs, 8 GB RAM) |
| **Cost** | $0.016 per build-minute |
| **Free Tier** | 120 minutes/day on n1-standard-1 only (doesn't apply to E2_HIGHCPU_8) |
| **Monthly Cost** | ~$5 for 30 deployments, ~$16 for 100 deployments |
| **Build Time** | 5-12 minutes with caching |

### GitHub Actions Alternative

| Aspect | Details |
|--------|---------|
| **Runners** | GitHub-hosted (2-core, 7 GB RAM) or self-hosted |
| **Cost** | Free for public repos, included minutes for private repos |
| **Free Tier (Private)** | 2,000 minutes/month (Team/Enterprise: 3,000-50,000) |
| **Paid Cost** | $0.008 per minute after free tier (Linux) |
| **Estimated Build Time** | 8-15 minutes with caching (slower than Cloud Build) |
| **Monthly Cost** | $0 if within free tier, ~$3-8 for 30 deployments if exceeding |

### Cost Analysis

**For a private repository with 30 deployments/month:**

#### GitHub Actions (Optimistic)
- Build time: ~10 minutes per deployment × 2 services = 20 minutes/deployment
- Total monthly minutes: 30 × 20 = 600 minutes
- **Cost**: $0 (well within 2,000 free minutes)

#### GitHub Actions (Conservative)
- Build time: ~12 minutes per deployment × 2 services = 24 minutes/deployment
- Total monthly minutes: 30 × 24 = 720 minutes
- **Cost**: $0 (within free tier)

#### For 100 deployments/month:
- Total minutes: 100 × 20 = 2,000 minutes
- **Cost**: $0 (exactly at free tier limit) or ~$8-16 if builds take longer

**Verdict**: GitHub Actions could save **~$5/month** for typical usage if staying within free tier.

## Pros and Cons

### GitHub Actions Advantages ✅

1. **Cost Savings**: Free tier is substantial (2,000-3,000 minutes/month)
2. **Integrated with GitHub**: Native integration with PRs, issues, and GitHub ecosystem
3. **Matrix Builds**: Easy to test multiple configurations in parallel
4. **Rich Marketplace**: Thousands of pre-built actions available
5. **Secrets Management**: Built-in secrets management with GitHub Secrets
6. **Better CI/CD Visibility**: Build status directly in GitHub UI
7. **Community Actions**: Reusable workflows from community
8. **Self-hosted Runners**: Option to run on your own infrastructure

### GitHub Actions Disadvantages ❌

1. **Slower Builds**: GitHub-hosted runners are less powerful (2 cores vs 8 cores)
2. **Docker Caching**: More complex to set up efficient Docker layer caching
3. **Google Cloud Integration**: Requires additional setup for GCP authentication
4. **Build Time**: Estimated 60-100% slower than current Cloud Build setup
5. **Free Tier Limits**: Can exceed free tier with high deployment frequency
6. **Network Transfer**: Pushing large images to Artifact Registry takes time

### Cloud Build Advantages ✅

1. **Faster Builds**: E2_HIGHCPU_8 machines are significantly faster
2. **Native GCP Integration**: Seamless authentication and integration
3. **BuildKit Caching**: Excellent Docker layer caching already configured
4. **Parallel Builds**: Current setup already optimized for parallel execution
5. **Already Configured**: Working setup with proven performance

### Cloud Build Disadvantages ❌

1. **Cost**: All minutes are billable (no free tier for E2_HIGHCPU_8)
2. **Separate Platform**: Build logs separate from GitHub PR workflow
3. **Manual Triggers**: Requires external triggers or scripts to run

## Recommended Hybrid Approach

The best strategy is to use **both** GitHub Actions and Cloud Build:

### GitHub Actions for:
- ✅ **PR validation builds**: Test/lint without deploying
- ✅ **Unit tests**: Run Maven tests on every PR
- ✅ **Integration tests**: Test application without deployment
- ✅ **Code quality checks**: Linting, security scanning, code coverage
- ✅ **Documentation builds**: Validate markdown, generate docs

### Cloud Build for:
- ✅ **Production deployments**: Fast, optimized container builds
- ✅ **Staging deployments**: Full deployment to test environments
- ✅ **Container image builds**: Leverage existing BuildKit caching

**Cost Impact**: Using GitHub Actions for PR validation while keeping Cloud Build for deployments could **reduce costs by 50-70%** by avoiding builds for PRs that don't get merged.

## Implementation Guide

### Phase 1: Add GitHub Actions for PR Validation

Create `.github/workflows/pr-validation.yml`:

```yaml
name: PR Validation

on:
  pull_request:
    branches: [main]
    paths:
      - 'core/**'
      - 'web/**'
      - 'receipt-parser/**'
      - 'pom.xml'
      - '**/pom.xml'
      - 'Dockerfile'
      - 'receipt-parser/Dockerfile'

jobs:
  test:
    name: Run Tests
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run core tests
        run: ./mvnw -pl core -am test
      
      - name: Run web tests
        run: ./mvnw -Pinclude-web -pl web -am test
      
      - name: Run receipt-parser tests
        run: ./mvnw -pl receipt-parser -am test

  lint:
    name: Code Quality
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
      
      - name: Run Maven verify
        run: ./mvnw verify -DskipTests

  docker-build-check:
    name: Verify Docker Builds
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      
      - name: Build web image (no push)
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./Dockerfile
          push: false
          cache-from: type=gha
          cache-to: type=gha,mode=max
          tags: pklnd-web:pr-${{ github.event.pull_request.number }}
      
      - name: Build receipt-parser image (no push)
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./receipt-parser/Dockerfile
          push: false
          cache-from: type=gha
          cache-to: type=gha,mode=max
          tags: pklnd-receipts:pr-${{ github.event.pull_request.number }}
```

**Estimated time**: 8-12 minutes per PR
**Cost**: Free (within 2,000 minute limit)
**Benefit**: Catch issues before merging, no cost impact on Cloud Build

### Phase 2: Add GitHub Actions for Deployments

A manually-triggered deployment workflow has been added at `.github/workflows/deploy-cloud-run.yml`. This workflow:
- Triggers manually via GitHub UI (Actions → Deploy to Cloud Run → Run workflow)
- Builds and pushes Docker images to Artifact Registry
- Deploys to Cloud Run using Terraform with persistent state in GCS
- Supports environment selection (production/staging)
- Provides deployment summary with service URLs
- Automatically cleans up old container images

The workflow uses a GCS bucket (`pklnd-terraform-state-<project-id>`) to store Terraform state, ensuring that existing Cloud Run services are updated rather than recreated on each deployment. The state bucket is created automatically on the first deployment run.

**To use the deployment workflow:**
1. Set up Workload Identity Federation (see Phase 3 below)
2. Configure GitHub secrets (see required secrets below)
3. Navigate to Actions → Deploy to Cloud Run → Run workflow
4. Select environment and region, then click "Run workflow"

**Alternative: Automatic deployments on push to main**

If you prefer automatic deployments instead of manual triggers, you can create `.github/workflows/deploy-auto.yml`:

```yaml
name: Deploy to Cloud Run

on:
  push:
    branches: [main]
    paths:
      - 'core/**'
      - 'web/**'
      - 'receipt-parser/**'
      - 'pom.xml'
      - '**/pom.xml'
      - 'Dockerfile'
      - 'receipt-parser/Dockerfile'
  workflow_dispatch:

env:
  PROJECT_ID: ${{ secrets.GCP_PROJECT_ID }}
  REGION: us-east1

jobs:
  build-and-deploy:
    name: Build and Deploy
    runs-on: ubuntu-latest
    
    permissions:
      contents: read
      id-token: write
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Authenticate to Google Cloud
        uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
          service_account: ${{ secrets.WIF_SERVICE_ACCOUNT }}
      
      - name: Set up Cloud SDK
        uses: google-github-actions/setup-gcloud@v2
      
      - name: Configure Docker for Artifact Registry
        run: gcloud auth configure-docker ${{ env.REGION }}-docker.pkg.dev
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      
      - name: Build and push web image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./Dockerfile
          push: true
          tags: |
            ${{ env.REGION }}-docker.pkg.dev/${{ env.PROJECT_ID }}/web/pklnd-web:${{ github.sha }}
            ${{ env.REGION }}-docker.pkg.dev/${{ env.PROJECT_ID }}/web/pklnd-web:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
          build-args: |
            GIT_BRANCH=${{ github.ref_name }}
            GIT_COMMIT=${{ github.sha }}
      
      - name: Build and push receipt-parser image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./receipt-parser/Dockerfile
          push: true
          tags: |
            ${{ env.REGION }}-docker.pkg.dev/${{ env.PROJECT_ID }}/receipts/pklnd-receipts:${{ github.sha }}
            ${{ env.REGION }}-docker.pkg.dev/${{ env.PROJECT_ID }}/receipts/pklnd-receipts:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
          build-args: |
            GIT_BRANCH=${{ github.ref_name }}
            GIT_COMMIT=${{ github.sha }}
      
      - name: Deploy to Cloud Run
        run: |
          # Deploy using Terraform or gcloud commands
          ./scripts/terraform/deploy_services.sh
```

### Phase 3: Set Up Workload Identity Federation

For secure authentication without service account keys, set up Workload Identity Federation:

#### Step 1: Get your GCP project number

```bash
PROJECT_ID="your-project-id"
PROJECT_NUMBER=$(gcloud projects describe "${PROJECT_ID}" --format="value(projectNumber)")
echo "Project Number: ${PROJECT_NUMBER}"
```

#### Step 2: Create Workload Identity Pool

```bash
gcloud iam workload-identity-pools create "github-actions" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --display-name="GitHub Actions Pool"
```

#### Step 3: Create Workload Identity Provider

```bash
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --location="global" \
  --workload-identity-pool="github-actions" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository_owner == 'pekelund-dev'" \
  --project="${PROJECT_ID}"
```

#### Step 4: Grant permissions to GitHub Actions

Replace `YOUR_GITHUB_ORG/YOUR_REPO` with your actual repository:

```bash
# Grant the cloud-run-runtime service account permission to be used by GitHub Actions
gcloud iam service-accounts add-iam-policy-binding "cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --project="${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-actions/attribute.repository/YOUR_GITHUB_ORG/YOUR_REPO"

# Also grant permissions to push to Artifact Registry
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"
```

#### Step 5: Get Workload Identity Provider name

```bash
gcloud iam workload-identity-pools providers describe "github-provider" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --workload-identity-pool="github-actions" \
  --format="value(name)"
```

This will output something like: `projects/123456789/locations/global/workloadIdentityPools/github-actions/providers/github-provider`

#### Step 6: Configure GitHub Secrets

Go to your GitHub repository → Settings → Secrets and variables → Actions → New repository secret

Add these secrets:
- `GCP_PROJECT_ID`: Your GCP project ID (e.g., `my-project-123`)
- `WIF_PROVIDER`: Full workload identity provider name from Step 5 (e.g., `projects/123456789/locations/global/workloadIdentityPools/github-actions/providers/github-provider`)
- `WIF_SERVICE_ACCOUNT`: Service account email (e.g., `cloud-run-runtime@my-project-123.iam.gserviceaccount.com`)

#### Step 7: Test the workflow

Navigate to GitHub Actions → Deploy to Cloud Run → Run workflow to test the deployment.

## Migration Strategy

### Recommended Approach: Gradual Migration

**Week 1-2: Testing Phase**
1. Add PR validation workflow (Phase 1)
2. Monitor build times and success rates
3. Adjust caching strategies if needed
4. Keep Cloud Build for deployments

**Week 3-4: Evaluation Phase**
1. Measure time savings and cost impact
2. Gather developer feedback on PR workflow
3. Decide whether to proceed with deployment migration

**Week 5-6: Optional Deployment Migration**
1. Set up Workload Identity Federation (Phase 3)
2. Add deployment workflow (Phase 2)
3. Run parallel deployments (both GitHub Actions and Cloud Build)
4. Compare performance and reliability

**Week 7+: Production**
1. Choose primary deployment method based on data
2. Keep secondary method as backup
3. Document final workflow

### Conservative Approach: Hybrid Forever

Keep Cloud Build for all deployments, use GitHub Actions only for PR validation:

**Benefits**:
- Maintains fast deployment times (5-12 minutes)
- No change to production deployment process
- Adds PR validation without cost increase
- Best of both worlds

**Estimated Monthly Cost**:
- Cloud Build: ~$5 (unchanged)
- GitHub Actions: $0 (within free tier for PR validation only)
- **Total**: ~$5/month

## Performance Comparison

| Metric | Cloud Build (Current) | GitHub Actions (Estimated) |
|--------|----------------------|---------------------------|
| **Build Time (Cold)** | 13-20 min | 20-30 min |
| **Build Time (Warm)** | 5-9 min | 8-15 min |
| **Parallel Builds** | Yes (native) | Yes (matrix) |
| **Docker Cache** | BuildKit inline | GitHub Actions cache |
| **Monthly Cost (30 deploys)** | ~$5 | $0-3 |
| **Monthly Cost (100 deploys)** | ~$16 | $0-10 |
| **Free Tier** | None for E2_HIGHCPU_8 | 2,000 minutes |

## Decision Matrix

Choose **GitHub Actions** if:
- ✅ You want to reduce costs (especially for low-volume deployments)
- ✅ You prioritize GitHub integration and PR workflows
- ✅ Build time is less critical (acceptable to wait 10-15 min vs 5-10 min)
- ✅ You deploy less than 50 times per month
- ✅ You want to leverage community actions and workflows

Keep **Cloud Build** if:
- ✅ Build speed is critical (5-12 min is important)
- ✅ You already have optimized BuildKit caching
- ✅ You deploy very frequently (100+ times per month)
- ✅ You value native GCP integration
- ✅ Cost of $5-16/month is acceptable for the speed benefit

Use **Hybrid Approach** if:
- ✅ You want PR validation without impacting deployment speed
- ✅ You want to save costs on PR builds that don't merge
- ✅ You want the best of both platforms
- ✅ You're willing to maintain two build systems

## Recommended Solution for This Project

Based on the analysis, I recommend a **Hybrid Approach**:

### Phase 1 (Immediate): Add GitHub Actions for PR Validation
- ✅ Implement PR validation workflow
- ✅ Run tests and linting on every PR
- ✅ Verify Docker builds without pushing
- ✅ **Cost**: $0 (within free tier)
- ✅ **Benefit**: Catch issues earlier, no impact on deployment speed

### Phase 2 (Optional): Keep Cloud Build for Deployments
- ✅ Continue using optimized Cloud Build for production deployments
- ✅ Maintain fast 5-12 minute build times
- ✅ Keep proven, reliable deployment process
- ✅ **Cost**: ~$5/month (unchanged)

### Expected Outcome
- **PR validation**: Free, catches issues before merge
- **Deployments**: Fast and reliable at ~$5/month
- **Total cost**: ~$5/month (same as now)
- **Developer experience**: Improved with earlier feedback
- **Build speed**: Maintained for deployments

## Next Steps

To implement the hybrid approach:

1. **Create PR validation workflow** (see Phase 1 example above)
2. **Test on a few PRs** to validate performance
3. **Monitor build times** using GitHub Actions insights
4. **Iterate on caching** if builds are too slow
5. **Document the workflow** for team members

If you want to proceed with full migration to GitHub Actions:

1. **Set up Workload Identity Federation** (see Phase 3)
2. **Create deployment workflow** (see Phase 2)
3. **Test deployments in parallel** with Cloud Build
4. **Compare results** over 2-3 weeks
5. **Make final decision** based on data

## Conclusion

**Recommendation**: Implement GitHub Actions for PR validation while keeping Cloud Build for deployments.

**Rationale**:
- Adds CI/CD capability without cost increase
- Maintains fast deployment times (5-12 minutes)
- Catches issues earlier in development
- No change to production workflow
- Low risk, high value

**Cost Impact**: $0 additional cost (within free tier)  
**Time Impact**: Adds 8-12 minutes of validation per PR  
**Developer Impact**: Better feedback loop, fewer failed deployments  

This hybrid approach provides the best balance of cost, speed, and developer experience for this project.
