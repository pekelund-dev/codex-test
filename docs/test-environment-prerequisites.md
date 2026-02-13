# Test Environment Setup - What's Actually Required

This document clarifies what's needed to set up the test environment, addressing the question: "Is the one-time setup all that's needed?"

## Short Answer

**No.** The "Quick Setup" in the PR description oversimplifies the process. Before running those commands, you need to:

1. Create OAuth 2.0 credentials in Google Cloud Console
2. Create a secret file with those credentials
3. Have an existing GCP project (or create a new one)

## Complete Prerequisites

### 1. GCP Project

**Option A: Use existing project** (Recommended)
- Use the same project as production
- Resources isolated by naming (`pklnd-web-test` vs `pklnd-web`)
- **No new project needed**

**Option B: Create separate project** (Maximum isolation)
```bash
export TEST_PROJECT_ID=your-test-project-id
gcloud projects create $TEST_PROJECT_ID --name="pklnd Test"
gcloud config set project $TEST_PROJECT_ID
# Link billing account
gcloud beta billing projects link $TEST_PROJECT_ID --billing-account=<billing-account-id>
```

### 2. OAuth Credentials

**MUST be done manually before setup:**

1. Go to [Google Cloud Console → APIs & Credentials](https://console.cloud.google.com/apis/credentials)
2. Create OAuth 2.0 Client ID (or reuse existing production credentials)
3. Note down the Client ID and Client Secret

### 3. Create Credentials File

**MUST be done before running setup script:**

```bash
cat > /tmp/pklnd-test-secret.json <<EOF
{
  "google_client_id": "your-client-id.apps.googleusercontent.com",
  "google_client_secret": "your-client-secret",
  "ai_studio_api_key": ""
}
EOF
```

## What the Setup Script Creates Automatically

When you run `setup_test_infrastructure.sh`, it **automatically creates**:

✅ **New Secret Manager secret**: `pklnd-app-config-test`
- Stores your OAuth credentials securely
- Created from the JSON file you provide

✅ **New Firestore database**: `receipts-db-test`
- Completely separate from production database
- Isolated data storage

✅ **New Storage bucket**: `pklnd-receipts-test-<project-id>`
- Separate bucket for test receipts
- Isolated from production files

✅ **Terraform state storage**: `gs://pklnd-terraform-state-<project>/infrastructure-test/`
- Isolated state management
- Prevents accidental production changes

## What is NOT Created (Reused from Production)

The following resources are **shared** between production and test to reduce costs:

🔄 **Service Accounts** (Not created, reused):
- `cloud-run-runtime@<project>.iam.gserviceaccount.com`
- `receipt-processor@<project>.iam.gserviceaccount.com`
- These must already exist from production setup

🔄 **Artifact Registry repositories** (Not created, reused):
- `web` repository
- `receipts` repository
- Container images for both environments stored here

🔄 **IAM roles and permissions** (Not created, reused):
- Service accounts already have necessary permissions
- No additional IAM configuration needed

## Complete Setup Flow

```
Prerequisites (Manual):
├─ 1. GCP Project exists (use existing or create new)
├─ 2. OAuth credentials created in Cloud Console
└─ 3. Credentials file created on local machine

     ↓

One-Time Setup Script (Automated):
├─ 4. setup_test_infrastructure.sh creates:
│     ├─ Secret Manager secret (with your credentials)
│     ├─ Firestore database
│     ├─ Storage bucket
│     └─ Terraform state setup

     ↓

Deploy Script (Automated):
├─ 5. deploy_to_test.sh creates:
│     ├─ Container images
│     ├─ Cloud Run services
│     └─ Service configurations

     ↓

Post-Deployment (Manual):
└─ 6. Update OAuth redirect URIs with actual Cloud Run URL
```

## Corrected Usage Example

### Full Setup (First Time)

```bash
# STEP 1: Prerequisites (if not done)
# - Create OAuth credentials in Cloud Console
# - Create credentials file (see above)
# - Set environment variables
export PROJECT_ID=your-gcp-project-id
export REGION=us-east1

# STEP 2: Run infrastructure setup
APP_SECRET_FILE=/tmp/pklnd-test-secret.json \
PROJECT_ID=$PROJECT_ID \
REGION=$REGION \
./scripts/terraform/setup_test_infrastructure.sh

# STEP 3: Deploy services
PROJECT_ID=$PROJECT_ID \
REGION=$REGION \
ENVIRONMENT=test \
./scripts/terraform/deploy_to_test.sh

# STEP 4: Update OAuth redirect URIs (manual)
# Get the URL from output and add to OAuth client in Cloud Console
```

### Subsequent Deployments

Once infrastructure is set up, deploying updates only requires:

```bash
export PROJECT_ID=your-gcp-project-id
ENVIRONMENT=test ./scripts/terraform/deploy_to_test.sh
```

## Time Estimates

- **Prerequisites setup**: 10-15 minutes (first time only)
- **Infrastructure setup**: 2-5 minutes (first time only)
- **Service deployment**: 5-10 minutes (every deployment)
- **OAuth redirect update**: 2-3 minutes (first time only)

**Total first-time setup: 20-35 minutes**

## What About Service Accounts?

**Why aren't new service accounts created?**

1. **Cost efficiency**: Each service account adds management overhead
2. **Shared permissions**: Both environments need the same GCP API access
3. **Data isolation**: Application configuration (not IAM) determines which database/bucket to use
4. **Simplicity**: Less IAM management, fewer potential permission issues

**Is this safe?**

Yes, because:
- Environment variables tell services which database/bucket to use
- Service names are different (`pklnd-web` vs `pklnd-web-test`)
- Data isolation is enforced at the application config level
- Both environments are controlled by the same team

**Want separate service accounts?**

If you need complete IAM isolation, use Option B (separate GCP project). The setup script can be modified to create new service accounts, but it adds complexity without much security benefit for most use cases.

## Summary Table

| What | Status | Where Created |
|------|--------|---------------|
| GCP Project | Required (existing or new) | Manual or existing |
| OAuth Credentials | Required (manual) | Google Cloud Console |
| Credentials JSON File | Required (manual) | Local filesystem |
| Secret Manager Secret | Auto-created | setup_test_infrastructure.sh |
| Firestore Database | Auto-created | setup_test_infrastructure.sh |
| Storage Bucket | Auto-created | setup_test_infrastructure.sh |
| Cloud Run Services | Auto-created | deploy_to_test.sh |
| Service Accounts | Reused from production | N/A (already exists) |
| Artifact Registry | Reused from production | N/A (already exists) |

## See Also

- Complete 37-step guide: `docs/IMPLEMENTATION_SUMMARY.md`
- Detailed setup instructions: `docs/test-environment-setup.md`
- Quick reference: `docs/test-environment-quick-reference.md`
- Architecture overview: `docs/test-environment-architecture.md`
