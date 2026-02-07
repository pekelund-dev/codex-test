# Test Environment Setup Guide

This guide provides step-by-step instructions for setting up a separate test environment for the pklnd application. The test environment allows you to deploy and test changes without affecting the production environment and its data.

## Overview

The test environment setup uses the same Terraform infrastructure code but with separate:
- Cloud Run service names (e.g., `pklnd-web-test` instead of `pklnd-web`)
- Firestore database (`receipts-db-test` instead of `receipts-db`)
- Storage bucket (`pklnd-receipts-test-<project>` instead of `pklnd-receipts-<project>`)
- Secret Manager secrets (`pklnd-app-config-test` instead of `pklnd-app-config`)
- Terraform state prefixes (to keep state isolated)

You can deploy the test environment to:
1. **Same GCP project** - Resources are isolated by naming (recommended for cost efficiency)
2. **Separate GCP project** - Complete isolation with separate billing and quotas

This guide covers both approaches.

## Prerequisites

Before starting, ensure you have:

- [Terraform](https://developer.hashicorp.com/terraform/downloads) 1.5 or newer installed
- [gcloud CLI](https://cloud.google.com/sdk/docs/install) installed and authenticated
- Access to a Google Cloud project with billing enabled
- Project Owner or Editor role in the target GCP project(s)
- OAuth 2.0 credentials for the test environment (can be same as production or separate)

## Option 1: Test Environment in Same Project (Recommended)

This approach keeps both production and test in the same GCP project with resource isolation through naming.

### Step 1: Set Environment Variables

```bash
export PROJECT_ID=your-gcp-project-id
export REGION=us-east1  # or your preferred region
```

### Step 2: Create Test OAuth Credentials (Optional)

If you want separate OAuth credentials for testing:

1. Go to [Google Cloud Console > APIs & Credentials](https://console.cloud.google.com/apis/credentials)
2. Create a new OAuth 2.0 Client ID (or reuse production credentials)
3. Add authorized redirect URIs:
   - `https://pklnd-web-test-<hash>-<region>.a.run.app/login/oauth2/code/google` (you'll get the exact URL after first deployment)
   - `http://localhost:8080/login/oauth2/code/google` (for local testing against test data)
4. Save the Client ID and Client Secret

### Step 3: Create Test Credentials Secret File

Create a JSON file with your test environment credentials:

```bash
cat > /tmp/pklnd-test-secret.json <<EOF
{
  "google_client_id": "your-test-client-id.apps.googleusercontent.com",
  "google_client_secret": "your-test-client-secret",
  "ai_studio_api_key": ""
}
EOF
```

**Important**: Keep this file secure and never commit it to version control.

### Step 4: Provision Test Infrastructure

Run the infrastructure setup script to create test-specific resources:

```bash
cd /path/to/codex-test
APP_SECRET_FILE=/tmp/pklnd-test-secret.json \
PROJECT_ID=$PROJECT_ID \
REGION=$REGION \
FIRESTORE_DATABASE_NAME=receipts-db-test \
BUCKET_NAME=pklnd-receipts-test-${PROJECT_ID} \
APP_SECRET_NAME=pklnd-app-config-test \
./scripts/terraform/setup_test_infrastructure.sh
```

This creates:
- ✅ Firestore database named `receipts-db-test`
- ✅ Storage bucket named `pklnd-receipts-test-<project-id>`
- ✅ Secret Manager secret named `pklnd-app-config-test` with your credentials
- ✅ Artifact Registry repositories (reuses existing `web` and `receipts` repos)
- ✅ Service accounts (reuses existing service accounts)
- ✅ Terraform state stored at `gs://pklnd-terraform-state-<project>/infrastructure-test/`

**Note**: Service accounts and Artifact Registry repositories are shared between production and test to minimize costs. Only data storage resources are isolated.

### Step 5: Deploy Test Services

Deploy the application services to the test environment:

```bash
PROJECT_ID=$PROJECT_ID \
REGION=$REGION \
ENVIRONMENT=test \
WEB_SERVICE_NAME=pklnd-web-test \
RECEIPT_SERVICE_NAME=pklnd-receipts-test \
APP_SECRET_NAME=pklnd-app-config-test \
FIRESTORE_DATABASE_NAME=receipts-db-test \
BUCKET_NAME=pklnd-receipts-test-${PROJECT_ID} \
./scripts/terraform/deploy_to_test.sh
```

This will:
1. Build container images for both services with timestamp tags
2. Push images to Artifact Registry
3. Deploy Cloud Run services with test-specific names
4. Configure services to use test database and bucket
5. Output the service URLs

The deployment takes approximately 5-10 minutes.

### Step 6: Update OAuth Redirect URIs

After the first deployment, you'll get the actual Cloud Run URLs. Update your OAuth client configuration:

1. Get the web service URL:
   ```bash
   cd infra/terraform/deployment
   terraform output -raw web_service_url
   ```

2. Add the callback URL to your OAuth client:
   - Go to [Google Cloud Console > APIs & Credentials](https://console.cloud.google.com/apis/credentials)
   - Edit your test OAuth client
   - Add: `<your-web-service-url>/login/oauth2/code/google`
   - Save changes

### Step 7: Verify the Test Environment

1. Open the test web service URL in your browser
2. Try logging in with Google OAuth
3. Upload a test receipt to verify storage works
4. Check that data is stored in the test Firestore database

To verify data isolation, check in the Cloud Console:
- **Firestore**: Should see `receipts-db-test` database
- **Storage**: Should see `pklnd-receipts-test-<project>` bucket
- **Cloud Run**: Should see `pklnd-web-test` and `pklnd-receipts-test` services

### Step 8: Configure GitHub Actions (Optional)

If you want to deploy from GitHub Actions:

1. Go to your repository Settings > Environments
2. Create a new environment called `test`
3. Add the same secrets as production but with test values if needed

Update the workflow to use test-specific variables when deploying to test environment.

## Option 2: Test Environment in Separate Project

For complete isolation, you can use a separate GCP project for testing.

### Step 1: Create a New GCP Project

```bash
export TEST_PROJECT_ID=your-test-project-id
gcloud projects create $TEST_PROJECT_ID --name="pklnd Test"
gcloud config set project $TEST_PROJECT_ID

# Link billing account
gcloud beta billing projects link $TEST_PROJECT_ID --billing-account=<your-billing-account-id>
```

### Step 2: Follow Same Steps as Option 1

Use the same steps as Option 1, but with `PROJECT_ID=$TEST_PROJECT_ID` for all commands.

Key differences:
- All resources are in the separate test project
- Billing is tracked separately
- IAM and security are completely isolated
- Requires managing two separate GCP projects

### Step 3: Clean Up Resources

When you want to delete the test project:

```bash
gcloud projects delete $TEST_PROJECT_ID
```

This removes all resources and stops all billing.

## Deploying Updates to Test Environment

To deploy code changes to the test environment:

```bash
cd /path/to/codex-test
PROJECT_ID=$PROJECT_ID \
REGION=$REGION \
ENVIRONMENT=test \
APP_SECRET_NAME=pklnd-app-config-test \
FIRESTORE_DATABASE_NAME=receipts-db-test \
./scripts/terraform/deploy_to_test.sh
```

The script will:
1. Build new container images with fresh code
2. Deploy updated services
3. Keep all existing data intact

## Managing Test Data

### Clearing Test Data

To reset the test environment data without redeploying:

```bash
# Clear Firestore test database
gcloud firestore databases delete receipts-db-test --project=$PROJECT_ID

# Recreate empty database
# Re-run setup_test_infrastructure.sh

# Clear storage bucket
gsutil -m rm -r gs://pklnd-receipts-test-${PROJECT_ID}/**
```

### Copying Production Data to Test (Use with Caution)

If you need to test with production-like data:

```bash
# Export from production database
gcloud firestore export gs://pklnd-receipts-${PROJECT_ID}/firestore-backup \
  --database=receipts-db \
  --project=$PROJECT_ID

# Import to test database
gcloud firestore import gs://pklnd-receipts-${PROJECT_ID}/firestore-backup \
  --database=receipts-db-test \
  --project=$PROJECT_ID
```

**Warning**: Ensure no PII or sensitive data is copied if test environment has different access controls.

## Tearing Down Test Environment

To remove all test environment resources:

### If using same project (Option 1):

```bash
cd /path/to/codex-test

# Destroy test services
cd infra/terraform/deployment
terraform init -backend-config="bucket=pklnd-terraform-state-${PROJECT_ID}" \
  -backend-config="prefix=deployment-test"
terraform destroy -auto-approve

# Destroy test infrastructure
cd ../infrastructure
terraform init -backend-config="bucket=pklnd-terraform-state-${PROJECT_ID}" \
  -backend-config="prefix=infrastructure-test"
terraform destroy -auto-approve
```

### If using separate project (Option 2):

```bash
gcloud projects delete $TEST_PROJECT_ID
```

## Cost Considerations

The test environment incurs costs for:

- **Cloud Run**: Pay per request and instance time (typically minimal for testing)
- **Firestore**: Storage and operations (typically <$1/month for testing)
- **Cloud Storage**: Storage for receipt files (typically <$1/month for testing)
- **Cloud Build**: Build time (~$0.003/min, ~$0.30 per deployment)
- **Artifact Registry**: Storage for container images (~$0.10/GB/month)

### Cost Optimization Tips

1. **Scale to Zero**: Test Cloud Run services scale to 0 when not in use (no cost)
2. **Delete Old Images**: Clean up old container images from Artifact Registry
3. **Limit Test Data**: Don't store large amounts of test receipts
4. **Use Same Project**: Sharing resources between test and production reduces overhead
5. **Manual Builds**: Deploy to test only when needed, not on every commit

Estimated monthly cost for light testing usage: **$5-15/month**

## Troubleshooting

### Issue: "Secret not found" error

**Solution**: Ensure the test secret is created:
```bash
gcloud secrets describe pklnd-app-config-test --project=$PROJECT_ID
```

If not found, create it:
```bash
echo '{"google_client_id":"...","google_client_secret":"...","ai_studio_api_key":""}' | \
  gcloud secrets create pklnd-app-config-test --data-file=- --project=$PROJECT_ID
```

### Issue: OAuth redirect mismatch

**Solution**: Update the authorized redirect URIs in your OAuth client configuration to include the test Cloud Run URL.

### Issue: Firestore permission denied

**Solution**: Verify that the service account has the correct roles:
```bash
gcloud projects get-iam-policy $PROJECT_ID \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
```

Should include `roles/datastore.user`.

### Issue: Build failures

**Solution**: Check Cloud Build logs:
```bash
gcloud builds list --project=$PROJECT_ID --limit=5
gcloud builds log <BUILD_ID> --project=$PROJECT_ID
```

### Issue: Different database being used

**Solution**: Check the deployed service environment variables:
```bash
gcloud run services describe pklnd-web-test --region=$REGION --project=$PROJECT_ID --format=yaml | grep -A 20 "env:"
```

Verify `FIRESTORE_DATABASE_ID` is set to `receipts-db-test`.

## Summary Checklist

After completing this guide, you should have:

- [ ] Test infrastructure provisioned (Firestore, Storage, Secrets)
- [ ] Test Cloud Run services deployed
- [ ] OAuth configuration updated with test redirect URIs
- [ ] Verified test environment works independently
- [ ] Confirmed data isolation from production
- [ ] Documented test environment URLs and configuration
- [ ] (Optional) Configured CI/CD to deploy to test

## Next Steps

Now that your test environment is set up:

1. **Test new features**: Deploy feature branches to test before merging to production
2. **Integration testing**: Run automated tests against the test environment
3. **Performance testing**: Load test without affecting production
4. **Training**: Use test environment for training new team members
5. **Customer demos**: Show features to customers before production release

## Additional Resources

- [Terraform deployment guide](terraform-deployment.md)
- [Cloud Run documentation](https://cloud.google.com/run/docs)
- [Firestore documentation](https://cloud.google.com/firestore/docs)
- [Secret Manager best practices](https://cloud.google.com/secret-manager/docs/best-practices)
