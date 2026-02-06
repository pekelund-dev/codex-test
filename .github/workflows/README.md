# GitHub Actions Workflows

This directory contains GitHub Actions workflows for CI/CD automation.

## Available Workflows

### 1. PR Validation (`pr-validation.yml`)

**Trigger**: Automatically runs on pull requests to `main` branch

**Purpose**: Validates code changes before merging by:
- Running unit tests for all modules (core, web, receipt-parser)
- Running Maven verify (includes Spring Modulith checks)
- Building Docker images to verify they compile correctly
- Generating test result artifacts

**Duration**: ~8-12 minutes per PR

**Cost**: Free (within GitHub Actions 2,000 minute/month free tier)

**What it checks**:
- ✅ All tests pass
- ✅ Maven build succeeds
- ✅ Docker images build successfully
- ✅ No compilation errors

**Runs when these files change**:
- `core/**`, `web/**`, `receipt-parser/**`
- `pom.xml`, `**/pom.xml`
- `Dockerfile`, `receipt-parser/Dockerfile`
- `.github/workflows/**`

### 2. Deploy to Cloud Run (`deploy-cloud-run.yml`)

**Trigger**: Manually triggered via GitHub UI

**Purpose**: Builds and deploys both services (web and receipt-parser) to Google Cloud Run

**Duration**: ~10-15 minutes per deployment

**Cost**: Free if within GitHub Actions free tier, otherwise ~$0.08-0.15 per deployment

**What it does**:
1. Authenticates to Google Cloud via Workload Identity Federation
2. Builds Docker images for web and receipt-parser services
3. Pushes images to Google Artifact Registry
4. Deploys to Cloud Run using Terraform
5. Generates deployment summary with service URLs
6. Cleans up old container images (keeps last 3)

**How to use**:
1. Navigate to: Actions → Deploy to Cloud Run
2. Click "Run workflow"
3. Select:
   - **Environment**: production or staging
   - **Region**: GCP region (default: us-east1)
4. Click "Run workflow" button

**Requirements**:
- Workload Identity Federation configured
- GitHub secrets configured
- Terraform infrastructure already provisioned
- Service account with proper permissions

### 3. Release and Deploy to Cloud Run (`release-and-deploy.yml`)

**Trigger**: Manually triggered via GitHub UI

**Purpose**: Creates a semantic release (major/minor/patch), tags the release, deploys to Cloud Run, and then advances all Maven modules to the next `-SNAPSHOT` version.

**What it does**:
1. Reads the current Maven project version from the root `pom.xml`
2. Calculates the release version based on selected release type (`major`, `minor`, `patch`)
3. Updates all POM versions to the release version and commits the change
4. Tags the release commit as `vX.Y.Z` and pushes both commit and tag
5. Builds and deploys both Cloud Run services
6. Updates all POM versions to the next patch snapshot (`X.Y.(Z+1)-SNAPSHOT`) and commits the change

**How to use**:
1. Navigate to: Actions → Release and Deploy to Cloud Run
2. Click "Run workflow"
3. Select:
   - **Release type**: `major`, `minor`, or `patch`
   - **Environment**: `production` or `staging`
   - **Region**: GCP region (default: `us-east1`)
4. Click "Run workflow" button

## Setup Instructions

### Prerequisites

Before using the deployment workflow, you need to complete the setup:

**📘 Complete Setup Guide**: See [GitHub Actions Secrets Setup Guide](../../docs/github-actions-secrets-setup.md) for detailed step-by-step instructions.

Quick summary:

1. **Set up Workload Identity Federation** - Allow GitHub Actions to authenticate with GCP without service account keys

2. **Configure GitHub Secrets** - Add these three secrets (see guide for exact values):
   - `GCP_PROJECT_ID`: Your Google Cloud project ID
   - `WIF_PROVIDER`: Workload Identity Provider full resource name
   - `WIF_SERVICE_ACCOUNT`: Service account email (e.g., `cloud-run-runtime@PROJECT_ID.iam.gserviceaccount.com`)

3. **Grant Service Account Permissions** - The service account needs these roles:
   - `roles/run.admin` - Deploy and manage Cloud Run services
   - `roles/artifactregistry.writer` - Push Docker images
   - `roles/iam.serviceAccountUser` - Act as service account
   - `roles/storage.admin` - Manage GCS buckets for state
   - `roles/iam.workloadIdentityUser` - Used by GitHub Actions

4. **Provision Infrastructure** - Ensure Terraform infrastructure is already set up:
   ```bash
   PROJECT_ID=your-project ./scripts/terraform/apply_infrastructure.sh
   ```

### Testing the Setup

**Test PR validation** (automatic):
1. Create a pull request with any code change
2. Check the "Checks" tab on the PR
3. Verify all jobs complete successfully

**Test deployment** (manual):
1. Go to Actions → Deploy to Cloud Run
2. Click "Run workflow"
3. Select environment and region
4. Monitor the workflow execution
5. Check the summary for deployed service URLs

## Workflow Comparison

| Feature | PR Validation | Deploy to Cloud Run | Release and Deploy |
|---------|--------------|---------------------|--------------------|
| **Trigger** | Automatic (on PR) | Manual (workflow_dispatch) | Manual (workflow_dispatch) |
| **Purpose** | Validate changes | Deploy to environment | Create release, tag, deploy, and bump snapshot |
| **Builds Docker** | Yes (doesn't push) | Yes (pushes to registry) | Yes (pushes to registry) |
| **Runs Tests** | Yes | No | No |
| **Deploys** | No | Yes | Yes |
| **Updates Maven versions** | No | No | Yes |
| **Creates Git tag** | No | No | Yes |
| **Duration** | 8-12 minutes | 10-15 minutes | 12-18 minutes |
| **Cost** | Free | Free (if in tier) | Free (if in tier) |

## Cost Analysis

### GitHub Actions Free Tier
- **Public repos**: Unlimited minutes
- **Private repos**: 2,000 minutes/month (Linux runners)

### Estimated Usage
- **PR validation**: ~10 min/PR
- **Deployments**: ~12 min/deployment

**Example monthly usage (30 PRs + 10 deployments)**:
- PRs: 30 × 10 min = 300 minutes
- Deployments: 10 × 12 min = 120 minutes
- **Total**: 420 minutes (within free tier)

## Troubleshooting

### PR validation fails
1. Check the failing job in the Actions tab
2. Review test output in the artifacts
3. Fix the issue and push a new commit

### Deployment fails with authentication error
1. Verify Workload Identity Federation is set up correctly
2. Check that all required secrets are configured
3. Ensure service account has necessary permissions (see [setup guide](../../docs/github-actions-secrets-setup.md) for details):
   - `roles/iam.workloadIdentityUser`
   - `roles/artifactregistry.writer`
   - `roles/run.admin`
   - `roles/iam.serviceAccountUser`
   - `roles/storage.admin`

**See [Troubleshooting Section](../../docs/github-actions-secrets-setup.md#troubleshooting) in the setup guide for specific error messages and solutions.**

### Deployment fails with "image not found"
1. Check that Artifact Registry repositories exist:
   - `web` repository for web images
   - `receipts` repository for receipt-parser images
2. Verify service account has push permissions to Artifact Registry

### Terraform deployment fails
1. Ensure infrastructure is provisioned: `./scripts/terraform/apply_infrastructure.sh`
2. Check Terraform state is accessible
3. Verify all required Terraform variables are set

## Additional Resources

- [GitHub Actions CI/CD Guide](../../docs/github-actions-ci-cd-guide.md) - Complete guide with cost comparison and setup instructions
- [Cloud Build Cost Analysis](../../docs/cloud-build-cost-analysis.md) - Comparison with Cloud Build approach
- [Build Performance Optimizations](../../docs/build-performance-optimizations.md) - Performance optimization details

## Migration Path

**Current setup**: Using Cloud Build for all deployments (~$5/month)

**Recommended hybrid approach**:
1. ✅ Keep PR validation on GitHub Actions (free)
2. ✅ Use manual GitHub Actions deployment for occasional deploys (free if within tier)
3. ✅ Keep Cloud Build for high-frequency production deployments (faster)

**Full migration**: Use GitHub Actions for all builds and deployments (saves ~$5/month but 60-100% slower)

Choose the approach that best fits your deployment frequency and speed requirements.
