# Quick Deployment Setup Reference

This is a quick reference for setting up GitHub Actions deployments. For complete details, see [docs/github-actions-secrets-setup.md](../docs/github-actions-secrets-setup.md).

## Required GitHub Secrets

Add these three secrets in **Settings → Secrets and variables → Actions**:

| Secret Name | Description | Where to Find |
|------------|-------------|---------------|
| `GCP_PROJECT_ID` | Your GCP project ID | Run: `gcloud config get-value project` |
| `WIF_PROVIDER` | Workload Identity Provider | See Step 6 in [setup guide](../docs/github-actions-secrets-setup.md#step-6-get-workload-identity-provider-full-name) |
| `WIF_SERVICE_ACCOUNT` | Service account email | Format: `cloud-run-runtime@PROJECT_ID.iam.gserviceaccount.com` |

## Quick Setup Commands

```bash
# 1. Set your project
export PROJECT_ID="your-project-id"
export PROJECT_NUMBER=$(gcloud projects describe "${PROJECT_ID}" --format="value(projectNumber)")

# 2. Create Workload Identity Pool (if not exists)
gcloud iam workload-identity-pools create "github-actions" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --display-name="GitHub Actions Pool"

# 3. Create OIDC Provider (replace YOUR_GITHUB_USERNAME)
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --location="global" \
  --workload-identity-pool="github-actions" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository_owner == 'YOUR_GITHUB_USERNAME'" \
  --project="${PROJECT_ID}"

# 4. Create service account (if not exists)
gcloud iam service-accounts create cloud-run-runtime \
  --display-name="Cloud Run Runtime Service Account" \
  --project="${PROJECT_ID}"

# 5. Grant permissions (replace YOUR_GITHUB_USERNAME/YOUR_REPO)
gcloud iam service-accounts add-iam-policy-binding \
  cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com \
  --project="${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-actions/attribute.repository/YOUR_GITHUB_USERNAME/YOUR_REPO"

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/storage.admin"

# 6. Get WIF_PROVIDER value for GitHub Secret
gcloud iam workload-identity-pools providers describe "github-provider" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --workload-identity-pool="github-actions" \
  --format="value(name)"
```

## Common Issues

### PERMISSION_DENIED: Permission 'run.services.get' denied

**Cause**: Service account lacks Cloud Run permissions.

**Fix**:
```bash
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.admin"
```

### Failed to authenticate with Workload Identity

**Cause**: Incorrect WIF configuration or GitHub secrets.

**Fix**: 
1. Double-check the `WIF_PROVIDER` secret matches the output from command in step 6
2. Verify `attribute.repository` in step 3 matches your GitHub owner/repo
3. Ensure the workflow has `id-token: write` permission

### Repository not found in Artifact Registry

**Cause**: Artifact Registry repositories don't exist.

**Fix**:
```bash
gcloud artifacts repositories create web \
  --repository-format=docker \
  --location=us-east1 \
  --project="${PROJECT_ID}"

gcloud artifacts repositories create receipts \
  --repository-format=docker \
  --location=us-east1 \
  --project="${PROJECT_ID}"
```

## Verification

### Test Authentication
```bash
# Verify service account permissions
gcloud projects get-iam-policy "${PROJECT_ID}" \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --format="table(bindings.role)"
```

Expected roles:
- ✅ roles/run.admin
- ✅ roles/artifactregistry.writer
- ✅ roles/iam.serviceAccountUser
- ✅ roles/storage.admin

### Test Deployment
1. Go to **Actions** → **Deploy to Cloud Run**
2. Click **Run workflow**
3. Select environment and region
4. Monitor execution

## Full Documentation

For complete setup instructions, troubleshooting, and security best practices:
- 📘 [GitHub Actions Secrets Setup Guide](../docs/github-actions-secrets-setup.md)
- 📘 [GitHub Actions CI/CD Guide](../docs/github-actions-ci-cd-guide.md)
- 📘 [Workflows README](./workflows/README.md)
