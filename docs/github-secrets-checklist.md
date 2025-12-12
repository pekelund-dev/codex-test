# GitHub Actions Deployment - Setup Checklist

Use this checklist to verify your GitHub Actions deployment is configured correctly.

## Prerequisites Checklist

### 1. Google Cloud Project Setup

- [ ] GCP project created and active
- [ ] Billing enabled on the project
- [ ] You have Owner or Editor role on the project

### 2. Required APIs Enabled

Run this command to enable all required APIs:

```bash
export PROJECT_ID="your-project-id"

gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  --project="${PROJECT_ID}"
```

- [ ] Cloud Run API enabled
- [ ] Artifact Registry API enabled
- [ ] Cloud Build API enabled
- [ ] IAM API enabled
- [ ] IAM Credentials API enabled

### 3. Artifact Registry Repositories

```bash
# Create repositories
gcloud artifacts repositories create web \
  --repository-format=docker \
  --location=us-east1 \
  --project="${PROJECT_ID}"

gcloud artifacts repositories create receipts \
  --repository-format=docker \
  --location=us-east1 \
  --project="${PROJECT_ID}"
```

- [ ] `web` repository created in Artifact Registry
- [ ] `receipts` repository created in Artifact Registry

### 4. Workload Identity Federation

```bash
# Get project number
export PROJECT_NUMBER=$(gcloud projects describe "${PROJECT_ID}" --format="value(projectNumber)")
echo "Project Number: ${PROJECT_NUMBER}"

# Create pool
gcloud iam workload-identity-pools create "github-actions" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --display-name="GitHub Actions Pool"

# Create provider (replace YOUR_GITHUB_USERNAME)
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --location="global" \
  --workload-identity-pool="github-actions" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository_owner == 'YOUR_GITHUB_USERNAME'" \
  --project="${PROJECT_ID}"
```

- [ ] Workload Identity Pool `github-actions` created
- [ ] OIDC Provider `github-provider` created
- [ ] Provider attribute condition matches your GitHub username

### 5. Service Account

```bash
# Create service account
gcloud iam service-accounts create cloud-run-runtime \
  --display-name="Cloud Run Runtime Service Account" \
  --project="${PROJECT_ID}"

# Verify it exists
gcloud iam service-accounts describe \
  cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com \
  --project="${PROJECT_ID}"
```

- [ ] Service account `cloud-run-runtime` created
- [ ] Service account email: `cloud-run-runtime@PROJECT_ID.iam.gserviceaccount.com`

### 6. Service Account Permissions

```bash
# Grant all required permissions (replace YOUR_GITHUB_USERNAME/YOUR_REPO)
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
```

Verify permissions:
```bash
gcloud projects get-iam-policy "${PROJECT_ID}" \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --format="table(bindings.role)"
```

- [ ] `roles/iam.workloadIdentityUser` granted
- [ ] `roles/run.admin` granted
- [ ] `roles/artifactregistry.writer` granted
- [ ] `roles/iam.serviceAccountUser` granted
- [ ] `roles/storage.admin` granted

### 7. GitHub Secrets

Get the WIF provider name:
```bash
gcloud iam workload-identity-pools providers describe "github-provider" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --workload-identity-pool="github-actions" \
  --format="value(name)"
```

Navigate to GitHub repository → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

Add these three secrets:

- [ ] `GCP_PROJECT_ID` = Your project ID (e.g., `my-project-123`)
- [ ] `WIF_PROVIDER` = Full provider name (e.g., `projects/123456789/locations/global/workloadIdentityPools/github-actions/providers/github-provider`)
- [ ] `WIF_SERVICE_ACCOUNT` = Service account email (e.g., `cloud-run-runtime@my-project-123.iam.gserviceaccount.com`)

### 8. Infrastructure Provisioned

```bash
# Provision Terraform infrastructure
PROJECT_ID=your-project ./scripts/terraform/apply_infrastructure.sh
```

- [ ] Terraform infrastructure applied
- [ ] GCS bucket for Terraform state created
- [ ] Secret Manager secrets configured

### 9. Workflow Permissions

Check that `.github/workflows/deploy-cloud-run.yml` has:

```yaml
permissions:
  contents: read
  id-token: write
```

- [ ] Workflow has `id-token: write` permission
- [ ] Workflow has `contents: read` permission

## Testing

### Test Deployment

1. Go to GitHub repository → **Actions**
2. Select **Deploy to Cloud Run** workflow
3. Click **Run workflow**
4. Select:
   - Environment: `production`
   - Region: `us-east1`
5. Click **Run workflow** button
6. Monitor execution

Expected results:
- [ ] Authentication step succeeds
- [ ] Docker images build successfully
- [ ] Images push to Artifact Registry
- [ ] Services deploy to Cloud Run
- [ ] Deployment summary shows service URLs

### Verify Services

```bash
# Check web service
gcloud run services describe pklnd-web \
  --region=us-east1 \
  --project="${PROJECT_ID}"

# Check receipt-parser service
gcloud run services describe pklnd-receipts \
  --region=us-east1 \
  --project="${PROJECT_ID}"

# Get service URLs
gcloud run services list \
  --project="${PROJECT_ID}" \
  --region=us-east1
```

- [ ] `pklnd-web` service is running
- [ ] `pklnd-receipts` service is running
- [ ] Both services have public URLs

## Troubleshooting

If deployment fails, see the [Troubleshooting section](./github-actions-secrets-setup.md#troubleshooting) in the setup guide.

Common issues:
- **PERMISSION_DENIED**: Service account lacks permissions → Re-run permission commands
- **Authentication failed**: WIF not configured correctly → Verify secrets match exactly
- **Repository not found**: Artifact Registry repos missing → Create with commands in step 3
- **Terraform state error**: Infrastructure not provisioned → Run apply_infrastructure.sh

## Complete Documentation

For detailed explanations and additional context:

- 📘 [GitHub Actions Secrets Setup Guide](./github-actions-secrets-setup.md) - Complete setup instructions
- 📘 [Quick Reference](./.github/DEPLOYMENT_SETUP.md) - Copy-paste commands
- 📘 [Workflows README](../.github/workflows/README.md) - Workflow usage guide
- 📘 [GitHub Actions CI/CD Guide](./github-actions-ci-cd-guide.md) - Architecture and cost analysis

## Summary

✅ Once all items are checked:
1. Your GitHub Actions workflow can authenticate with GCP
2. Container images can be built and pushed to Artifact Registry
3. Services can be deployed to Cloud Run
4. No service account keys are stored in GitHub (secure!)
5. Deployments use short-lived tokens via Workload Identity Federation

🎉 You're ready to deploy!
