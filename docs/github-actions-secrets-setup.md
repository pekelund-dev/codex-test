# GitHub Actions Secrets Setup Guide

This guide explains how to configure GitHub Secrets and Google Cloud permissions for the GitHub Actions deployment workflow.

## Overview

The GitHub Actions deployment workflow (`deploy-cloud-run.yml`) uses **Workload Identity Federation (WIF)** to authenticate with Google Cloud Platform. This is the recommended approach as it eliminates the need to store service account keys as secrets, which is a security best practice.

## Why We Don't Store Credential Files

The error message you may have seen references a temporary credentials file like `gha-creds-c66c31db4f3b56a3.json`. This file is:
- ✅ **Automatically generated** by the `google-github-actions/auth` action during workflow execution
- ✅ **Never committed** to the repository (and shouldn't be)
- ✅ **Temporary** and only exists during the workflow run
- ✅ **Secure** because it uses Workload Identity Federation instead of long-lived service account keys

This is the modern, secure way to authenticate GitHub Actions with Google Cloud.

## Required GitHub Secrets

You need to configure **three secrets** in your GitHub repository:

| Secret Name | Description | Example Value |
|------------|-------------|---------------|
| `GCP_PROJECT_ID` | Your Google Cloud Project ID | `my-project-123` |
| `WIF_PROVIDER` | Full Workload Identity Provider resource name | `projects/123456789/locations/global/workloadIdentityPools/github-actions/providers/github-provider` |
| `WIF_SERVICE_ACCOUNT` | Service account email for Cloud Run deployments | `cloud-run-runtime@my-project-123.iam.gserviceaccount.com` |

## Step-by-Step Setup

### Step 1: Get Your GCP Project Information

```bash
# Set your project ID
export PROJECT_ID="your-project-id-here"

# Get your project number (needed for Step 2)
export PROJECT_NUMBER=$(gcloud projects describe "${PROJECT_ID}" --format="value(projectNumber)")
echo "Project Number: ${PROJECT_NUMBER}"
```

### Step 2: Create Workload Identity Pool

Create a Workload Identity Pool to allow GitHub Actions to authenticate:

```bash
gcloud iam workload-identity-pools create "github-actions" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --display-name="GitHub Actions Pool"
```

**Note**: If the pool already exists, you'll see an error message. This is fine—just continue to Step 3.

### Step 3: Create Workload Identity Provider

Create an OIDC provider that GitHub Actions will use:

```bash
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --location="global" \
  --workload-identity-pool="github-actions" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository_owner == 'YOUR_GITHUB_USERNAME'" \
  --project="${PROJECT_ID}"
```

**Important**: Replace `YOUR_GITHUB_USERNAME` with your actual GitHub username or organization name.

### Step 4: Verify or Create Service Account

Check if the `cloud-run-runtime` service account exists:

```bash
gcloud iam service-accounts describe cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com \
  --project="${PROJECT_ID}"
```

If it doesn't exist, create it:

```bash
gcloud iam service-accounts create cloud-run-runtime \
  --display-name="Cloud Run Runtime Service Account" \
  --project="${PROJECT_ID}"
```

### Step 5: Grant Service Account Permissions

The service account needs several permissions to deploy to Cloud Run:

```bash
# Allow the service account to be used by GitHub Actions via Workload Identity
# Replace YOUR_GITHUB_USERNAME/YOUR_REPO with your actual GitHub repository (e.g., myusername/myrepo)
gcloud iam service-accounts add-iam-policy-binding \
  cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com \
  --project="${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-actions/attribute.repository/YOUR_GITHUB_USERNAME/YOUR_REPO"

# Grant Cloud Run Admin permissions (to create/update services)
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.admin"

# Grant Artifact Registry permissions (to push/pull Docker images)
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"

# Grant Service Account User role (to deploy Cloud Run services)
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"

# Grant Storage permissions (for storing build artifacts and state files)
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/storage.admin"
```

### Step 6: Get Workload Identity Provider Full Name

Retrieve the full resource name of your Workload Identity Provider:

```bash
gcloud iam workload-identity-pools providers describe "github-provider" \
  --project="${PROJECT_ID}" \
  --location="global" \
  --workload-identity-pool="github-actions" \
  --format="value(name)"
```

This will output something like:
```
projects/123456789/locations/global/workloadIdentityPools/github-actions/providers/github-provider
```

**Save this value**—you'll need it for GitHub Secrets.

### Step 7: Configure GitHub Secrets

Now add the secrets to your GitHub repository:

1. Navigate to your GitHub repository
2. Go to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add the following three secrets:

#### Secret 1: GCP_PROJECT_ID
- **Name**: `GCP_PROJECT_ID`
- **Value**: Your Google Cloud project ID (e.g., `my-project-123`)

#### Secret 2: WIF_PROVIDER
- **Name**: `WIF_PROVIDER`
- **Value**: The full Workload Identity Provider name from Step 6
  ```
  projects/123456789/locations/global/workloadIdentityPools/github-actions/providers/github-provider
  ```

#### Secret 3: WIF_SERVICE_ACCOUNT
- **Name**: `WIF_SERVICE_ACCOUNT`
- **Value**: The service account email
  ```
  cloud-run-runtime@my-project-123.iam.gserviceaccount.com
  ```

Replace `my-project-123` with your actual project ID.

## Verification

### Verify Service Account Permissions

Check that the service account has the required roles:

```bash
gcloud projects get-iam-policy "${PROJECT_ID}" \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --format="table(bindings.role)"
```

You should see:
- `roles/run.admin`
- `roles/artifactregistry.writer`
- `roles/iam.serviceAccountUser`
- `roles/storage.admin`

### Verify Workload Identity Binding

Check that the service account can be used by GitHub Actions:

```bash
gcloud iam service-accounts get-iam-policy \
  cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com \
  --format=json
```

Look for a binding with:
- `role: roles/iam.workloadIdentityUser`
- `members` containing a principal set that includes your GitHub repository

### Test the Deployment Workflow

1. Go to your GitHub repository
2. Navigate to **Actions** → **Deploy to Cloud Run**
3. Click **Run workflow**
4. Select **production** environment and **us-east1** region
5. Click **Run workflow**
6. Monitor the workflow execution

If everything is configured correctly, the workflow should:
- ✅ Authenticate to Google Cloud successfully
- ✅ Build and push Docker images to Artifact Registry
- ✅ Deploy services to Cloud Run
- ✅ Display deployment summary with service URLs

## Troubleshooting

### Error: PERMISSION_DENIED on run.services.get

**Problem**: The service account doesn't have permission to access Cloud Run services.

**Solution**: Grant Cloud Run Admin role:
```bash
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.admin"
```

### Error: PERMISSION_DENIED on artifactregistry operations

**Problem**: The service account can't push Docker images to Artifact Registry.

**Solution**: Grant Artifact Registry Writer role:
```bash
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.writer"
```

### Error: Failed to authenticate with Workload Identity

**Problem**: The Workload Identity Federation is not configured correctly.

**Solutions**:

1. Verify the WIF_PROVIDER secret matches the output from Step 6
2. Ensure the attribute condition in Step 3 matches your GitHub owner name
3. Check that the principalSet in Step 5 matches your repository name
4. Verify the GitHub Actions workflow has `id-token: write` permission:
   ```yaml
   permissions:
     contents: read
     id-token: write
   ```

### Error: Repository not found or access denied

**Problem**: The Artifact Registry repositories don't exist.

**Solution**: Create the required repositories:
```bash
# Create web repository
gcloud artifacts repositories create web \
  --repository-format=docker \
  --location=us-east1 \
  --project="${PROJECT_ID}"

# Create receipts repository
gcloud artifacts repositories create receipts \
  --repository-format=docker \
  --location=us-east1 \
  --project="${PROJECT_ID}"
```

### Error: Terraform state not accessible

**Problem**: The workflow can't access the Terraform state bucket.

**Solution**: Ensure the infrastructure is provisioned first:
```bash
PROJECT_ID=your-project ./scripts/terraform/apply_infrastructure.sh
```

## Required GCP APIs

Make sure these APIs are enabled in your Google Cloud project:

```bash
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  --project="${PROJECT_ID}"
```

## Security Best Practices

✅ **DO**:
- Use Workload Identity Federation (as described in this guide)
- Limit service account permissions to only what's needed
- Use separate service accounts for different environments (production, staging)
- Regularly audit service account permissions
- Enable Cloud Audit Logs to track service account usage

❌ **DON'T**:
- Store service account key files as GitHub Secrets
- Grant overly broad permissions like `roles/owner` or `roles/editor`
- Use the same service account across multiple projects
- Commit credential files to the repository
- Share service account keys in chat or documentation

## Alternative: Using Service Account Keys (Not Recommended)

If you cannot use Workload Identity Federation (e.g., due to organizational policies), you can use service account keys:

1. Create a service account key:
   ```bash
   gcloud iam service-accounts keys create key.json \
     --iam-account=cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com
   ```

2. Add the entire JSON content as a GitHub Secret named `GCP_SA_KEY`

3. Update `.github/workflows/deploy-cloud-run.yml`:
   ```yaml
   - name: Authenticate to Google Cloud
     uses: google-github-actions/auth@v2
     with:
       credentials_json: ${{ secrets.GCP_SA_KEY }}
   ```

⚠️ **Warning**: This approach is less secure because:
- Keys are long-lived credentials that don't expire automatically
- If the key is compromised, it provides full access until manually revoked
- Key rotation requires manual updates to GitHub Secrets

## Additional Resources

- [Workload Identity Federation Documentation](https://cloud.google.com/iam/docs/workload-identity-federation)
- [GitHub Actions OIDC Documentation](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect)
- [Google GitHub Actions Auth](https://github.com/google-github-actions/auth)
- [Cloud Run IAM Roles](https://cloud.google.com/run/docs/reference/iam/roles)
- [GitHub Actions CI/CD Guide](./github-actions-ci-cd-guide.md)

## Summary

To successfully deploy using GitHub Actions, you need:

1. **Workload Identity Federation**: Configured to allow GitHub Actions to assume a Google Cloud service account
2. **Service Account**: Created with appropriate Cloud Run, Artifact Registry, and Storage permissions
3. **GitHub Secrets**: Three secrets (GCP_PROJECT_ID, WIF_PROVIDER, WIF_SERVICE_ACCOUNT) configured in your repository
4. **GCP APIs**: Enabled in your Google Cloud project
5. **Infrastructure**: Provisioned using the Terraform scripts

Once configured, the workflow will automatically:
- Authenticate using short-lived tokens from GitHub
- Build and push container images
- Deploy services to Cloud Run
- Clean up old artifacts

No credential files need to be committed to the repository, making this approach both secure and maintainable.
