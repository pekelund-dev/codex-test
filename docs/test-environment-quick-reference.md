# Test Environment Quick Reference

Quick commands for working with the test environment.

## Prerequisites

Set your project ID:
```bash
export PROJECT_ID=your-gcp-project-id
export REGION=us-east1
```

## Initial Setup (One-time)

### 1. Create test credentials file
```bash
cat > /tmp/pklnd-test-secret.json <<EOF
{
  "google_client_id": "your-test-client-id.apps.googleusercontent.com",
  "google_client_secret": "your-test-client-secret",
  "ai_studio_api_key": ""
}
EOF
```

### 2. Provision test infrastructure
```bash
APP_SECRET_FILE=/tmp/pklnd-test-secret.json \
PROJECT_ID=$PROJECT_ID \
REGION=$REGION \
FIRESTORE_DATABASE_NAME=receipts-db-test \
BUCKET_NAME=pklnd-receipts-test-${PROJECT_ID} \
APP_SECRET_NAME=pklnd-app-config-test \
./scripts/terraform/setup_test_infrastructure.sh
```

### 3. First deployment
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

### 4. Update OAuth redirect URIs
Get test URL and add to OAuth client:
```bash
cd infra/terraform/deployment
terraform output -raw web_service_url
# Add: <url>/login/oauth2/code/google to OAuth client
```

## Daily Usage

### Deploy changes to test
```bash
PROJECT_ID=$PROJECT_ID \
REGION=$REGION \
ENVIRONMENT=test \
APP_SECRET_NAME=pklnd-app-config-test \
FIRESTORE_DATABASE_NAME=receipts-db-test \
./scripts/terraform/deploy_to_test.sh
```

### Get test service URLs
```bash
cd infra/terraform/deployment
terraform init -backend-config="bucket=pklnd-terraform-state-${PROJECT_ID}" \
  -backend-config="prefix=deployment-test"
terraform output web_service_url
terraform output receipt_service_url
```

### View test service logs
```bash
# Web service logs
gcloud run services logs read pklnd-web-test \
  --region=$REGION \
  --project=$PROJECT_ID \
  --limit=50

# Receipt service logs
gcloud run services logs read pklnd-receipts-test \
  --region=$REGION \
  --project=$PROJECT_ID \
  --limit=50
```

### Check test service status
```bash
gcloud run services describe pklnd-web-test \
  --region=$REGION \
  --project=$PROJECT_ID

gcloud run services describe pklnd-receipts-test \
  --region=$REGION \
  --project=$PROJECT_ID
```

## Data Management

### View test Firestore data
```bash
# List documents in test database
gcloud firestore databases describe receipts-db-test --project=$PROJECT_ID

# Export test data
gcloud firestore export gs://pklnd-receipts-test-${PROJECT_ID}/backup \
  --database=receipts-db-test \
  --project=$PROJECT_ID
```

### Clear test data
```bash
# Delete all files in test bucket
gsutil -m rm -r gs://pklnd-receipts-test-${PROJECT_ID}/**

# To reset Firestore, you'll need to delete and recreate the database
# This requires re-running setup_test_infrastructure.sh
```

### List test receipts in bucket
```bash
gsutil ls gs://pklnd-receipts-test-${PROJECT_ID}/
```

## Troubleshooting

### Check secret exists
```bash
gcloud secrets describe pklnd-app-config-test --project=$PROJECT_ID
gcloud secrets versions access latest --secret=pklnd-app-config-test --project=$PROJECT_ID
```

### Update test secret
```bash
echo '{"google_client_id":"new-id","google_client_secret":"new-secret","ai_studio_api_key":""}' | \
  gcloud secrets versions add pklnd-app-config-test --data-file=- --project=$PROJECT_ID
```

### Check service environment variables
```bash
gcloud run services describe pklnd-web-test \
  --region=$REGION \
  --project=$PROJECT_ID \
  --format=yaml | grep -A 30 "env:"
```

### View recent builds
```bash
gcloud builds list --project=$PROJECT_ID --limit=5
```

### View specific build log
```bash
gcloud builds log <BUILD_ID> --project=$PROJECT_ID
```

## Cleanup

### Destroy test services only (keep infrastructure)
```bash
cd infra/terraform/deployment
terraform init -backend-config="bucket=pklnd-terraform-state-${PROJECT_ID}" \
  -backend-config="prefix=deployment-test"
terraform destroy -auto-approve
```

### Destroy everything (infrastructure + services)
```bash
# Services first
cd infra/terraform/deployment
terraform init -backend-config="bucket=pklnd-terraform-state-${PROJECT_ID}" \
  -backend-config="prefix=deployment-test"
terraform destroy -auto-approve

# Then infrastructure
cd ../infrastructure
terraform init -backend-config="bucket=pklnd-terraform-state-${PROJECT_ID}" \
  -backend-config="prefix=infrastructure-test"
terraform destroy -auto-approve
```

## GitHub Actions Deployment

Deploy from GitHub Actions:

1. Go to Actions tab in GitHub
2. Select "Deploy to Cloud Run" workflow
3. Click "Run workflow"
4. Select environment: **test**
5. Select region: **us-east1**
6. Click "Run workflow"

The workflow will build and deploy to test automatically.

## Resource Names Summary

| Resource | Production | Test |
|----------|-----------|------|
| Web Service | `pklnd-web` | `pklnd-web-test` |
| Receipt Service | `pklnd-receipts` | `pklnd-receipts-test` |
| Firestore DB | `receipts-db` | `receipts-db-test` |
| Storage Bucket | `pklnd-receipts-<project>` | `pklnd-receipts-test-<project>` |
| Secret | `pklnd-app-config` | `pklnd-app-config-test` |
| Terraform State Prefix | `deployment` | `deployment-test` |

## Tips

- Test services scale to 0 when idle (no cost)
- Use test environment for feature branches
- Keep test data minimal to save costs
- Regularly clean up old container images
- Use same OAuth client for both environments if testing login isn't critical
- Monitor costs in Cloud Console > Billing

## Common Workflows

### Test a feature branch
```bash
git checkout feature/my-new-feature
# Make changes
PROJECT_ID=$PROJECT_ID ENVIRONMENT=test ./scripts/terraform/deploy_to_test.sh
# Test at the URL provided
# If OK, merge to main and deploy to production
```

### Compare test vs production
```bash
# Get both URLs
gcloud run services describe pklnd-web --region=$REGION --format='value(status.url)'
gcloud run services describe pklnd-web-test --region=$REGION --format='value(status.url)'
# Open both and compare behavior
```

### Roll back test deployment
```bash
# List recent images
gcloud artifacts docker tags list \
  ${REGION}-docker.pkg.dev/${PROJECT_ID}/web/pklnd-web-test \
  --limit=10

# Deploy older image
gcloud run deploy pklnd-web-test \
  --image=${REGION}-docker.pkg.dev/${PROJECT_ID}/web/pklnd-web-test:<old-timestamp> \
  --region=$REGION \
  --project=$PROJECT_ID
```

For detailed information, see [docs/test-environment-setup.md](./test-environment-setup.md)
