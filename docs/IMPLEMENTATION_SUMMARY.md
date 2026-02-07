# Test Environment Implementation Summary

## Overview

This document summarizes the complete test environment implementation for the pklnd application. The test environment allows deploying and testing changes without affecting production data or services.

## What Was Implemented

### 1. Infrastructure Scripts

**File: `scripts/terraform/setup_test_infrastructure.sh`**
- Provisions test-specific infrastructure resources
- Creates separate Firestore database (`receipts-db-test`)
- Creates separate Storage bucket (`pklnd-receipts-test-<project>`)
- Creates separate Secret Manager secret (`pklnd-app-config-test`)
- Reuses shared resources (service accounts, Artifact Registry)
- Uses isolated Terraform state prefix (`infrastructure-test`)

**File: `scripts/terraform/deploy_to_test.sh`**
- Deploys application services to test environment
- Builds container images with timestamped tags
- Deploys to test-specific Cloud Run services (`pklnd-web-test`, `pklnd-receipts-test`)
- Configures services to use test database and bucket
- Uses isolated Terraform state prefix (`deployment-test`)
- Includes cleanup of old artifacts

### 2. Environment Configurations

**Directory: `infra/terraform/environments/`**
- `test.tfvars` - Test environment variable overrides
- `production.tfvars` - Production environment variables (for documentation)
- `README.md` - Explains environment configuration approach

### 3. GitHub Actions Integration

**Updated: `.github/workflows/deploy-cloud-run.yml`**
- Added "test" option to environment selector
- Dynamic service names based on selected environment
- Supports deploying to test with different service names

**Updated: `.github/workflows/release-and-deploy.yml`**
- Added "test" option to environment selector  
- Dynamic service names based on selected environment
- Supports releasing to test environment

### 4. Documentation

**File: `docs/test-environment-setup.md` (12KB)**
Complete setup guide including:
- Overview of test environment architecture
- Prerequisites and requirements
- Step-by-step setup instructions for same-project deployment
- Step-by-step setup instructions for separate-project deployment
- OAuth configuration instructions
- GitHub Actions integration
- Data management procedures
- Cost considerations and optimization tips
- Comprehensive troubleshooting guide
- Summary checklist

**File: `docs/test-environment-quick-reference.md` (6.5KB)**
Quick reference guide including:
- One-time setup commands
- Daily usage commands
- Data management commands
- Troubleshooting commands
- Cleanup procedures
- Common workflow examples
- Resource naming reference table

**Updated: `README.md`**
- Added "Test Environment" section
- Quick setup example
- Links to detailed documentation
- Feature summary

## Architecture

### Resource Isolation Strategy

The test environment uses **resource name differentiation** to isolate environments in the same GCP project:

| Resource Type | Production | Test |
|--------------|-----------|------|
| Web Service | `pklnd-web` | `pklnd-web-test` |
| Receipt Service | `pklnd-receipts` | `pklnd-receipts-test` |
| Firestore Database | `receipts-db` | `receipts-db-test` |
| Storage Bucket | `pklnd-receipts-<project>` | `pklnd-receipts-test-<project>` |
| Secret Manager | `pklnd-app-config` | `pklnd-app-config-test` |
| Terraform State | `deployment/` | `deployment-test/` |

### Shared Resources

The following resources are shared between environments to reduce costs:
- Service Accounts (`cloud-run-runtime`, `receipt-processor`)
- Artifact Registry repositories (`web`, `receipts`)
- GCP APIs and project configuration
- IAM roles and permissions

## Deployment Flow

### Initial Setup (One-time)

```
1. Create test credentials (OAuth, API keys)
   ↓
2. Run setup_test_infrastructure.sh
   - Creates Firestore database
   - Creates Storage bucket
   - Creates Secret Manager secret
   - Configures IAM permissions
   ↓
3. Run deploy_to_test.sh
   - Builds container images
   - Deploys Cloud Run services
   - Configures environment variables
   ↓
4. Update OAuth redirect URIs
   - Add test service URL to OAuth client
   ↓
5. Verify test environment works
```

### Subsequent Deployments

```
1. Make code changes
   ↓
2. Run deploy_to_test.sh
   - Builds new images
   - Updates Cloud Run services
   - Preserves all data
   ↓
3. Test changes in test environment
   ↓
4. If OK, deploy to production
```

## Usage Examples

### Deploy feature branch to test
```bash
git checkout feature/new-feature
PROJECT_ID=$PROJECT_ID ENVIRONMENT=test ./scripts/terraform/deploy_to_test.sh
# Test at the URL provided
```

### View test service logs
```bash
gcloud run services logs read pklnd-web-test --region=$REGION --limit=50
```

### Clear test data
```bash
gsutil -m rm -r gs://pklnd-receipts-test-${PROJECT_ID}/**
```

### Deploy via GitHub Actions
1. Go to Actions → Deploy to Cloud Run
2. Select environment: "test"
3. Click "Run workflow"

## Benefits

✅ **Data Isolation**: Test data completely separate from production
✅ **Risk Reduction**: Test breaking changes without affecting users
✅ **Cost Efficient**: Shares infrastructure, scales to zero when idle
✅ **Easy Setup**: Single command to provision and deploy
✅ **CI/CD Ready**: GitHub Actions integration included
✅ **Flexible**: Same project or separate project supported
✅ **Documented**: Comprehensive guides for setup and usage

## Validation Checklist

The implementation has been validated for:

- [x] Bash script syntax is valid
- [x] GitHub Actions YAML syntax is valid
- [x] All documentation files created
- [x] Scripts are executable
- [x] Environment configurations created
- [x] README updated with test environment info
- [x] Resource naming conflicts avoided
- [x] Terraform state isolation configured
- [x] Cost optimization considerations included
- [x] Troubleshooting guide provided

## Files Created/Modified

### New Files (10)
1. `scripts/terraform/setup_test_infrastructure.sh` (executable)
2. `scripts/terraform/deploy_to_test.sh` (executable)
3. `infra/terraform/environments/test.tfvars`
4. `infra/terraform/environments/production.tfvars`
5. `infra/terraform/environments/README.md`
6. `docs/test-environment-setup.md`
7. `docs/test-environment-quick-reference.md`
8. `docs/IMPLEMENTATION_SUMMARY.md` (this file)

### Modified Files (3)
1. `.github/workflows/deploy-cloud-run.yml`
2. `.github/workflows/release-and-deploy.yml`
3. `README.md`

## Manual Steps Required

As requested in the issue, here are ALL manual steps to setup the test environment:

### Prerequisites Setup
1. Install Terraform 1.5+ on local machine
2. Install gcloud CLI on local machine
3. Authenticate: `gcloud auth login`
4. Set project: `export PROJECT_ID=your-gcp-project-id`
5. Set region: `export REGION=us-east1`

### OAuth Credentials Setup
6. Go to Google Cloud Console → APIs & Credentials
7. Create OAuth 2.0 Client ID (or use existing)
8. Note the Client ID and Client Secret

### Test Infrastructure Provisioning
9. Create credentials file:
   ```bash
   cat > /tmp/pklnd-test-secret.json <<EOF
   {
     "google_client_id": "your-test-client-id",
     "google_client_secret": "your-test-client-secret",
     "ai_studio_api_key": ""
   }
   EOF
   ```
10. Run infrastructure setup:
    ```bash
    cd /path/to/codex-test
    APP_SECRET_FILE=/tmp/pklnd-test-secret.json \
    PROJECT_ID=$PROJECT_ID \
    REGION=$REGION \
    ./scripts/terraform/setup_test_infrastructure.sh
    ```
11. Wait for infrastructure creation (~2-5 minutes)

### Service Deployment
12. Run test deployment:
    ```bash
    PROJECT_ID=$PROJECT_ID \
    REGION=$REGION \
    ENVIRONMENT=test \
    ./scripts/terraform/deploy_to_test.sh
    ```
13. Wait for build and deployment (~5-10 minutes)
14. Note the output URLs for web and receipt services

### OAuth Configuration
15. Copy the web service URL from output
16. Go to Google Cloud Console → APIs & Credentials
17. Edit your OAuth 2.0 Client
18. Add authorized redirect URI: `<web-service-url>/login/oauth2/code/google`
19. Save changes
20. Wait ~5 minutes for OAuth configuration to propagate

### Verification
21. Open test web service URL in browser
22. Verify login page loads
23. Test Google OAuth login
24. Upload a test receipt
25. Verify receipt appears in dashboard
26. Check Firestore Console for `receipts-db-test` database
27. Check Storage Console for `pklnd-receipts-test-<project>` bucket

### GitHub Actions Setup (Optional)
28. Go to GitHub repository → Settings → Environments
29. Verify "test" environment exists (or create it)
30. Go to Actions tab
31. Select "Deploy to Cloud Run" workflow
32. Click "Run workflow"
33. Select environment: "test"
34. Click "Run workflow" button
35. Wait for deployment to complete
36. Verify services updated successfully

### Cleanup Test Credentials
37. Delete local credentials file: `rm /tmp/pklnd-test-secret.json`

**Total Manual Steps: 37**
**Estimated Total Time: 30-45 minutes** (including wait times)

## Next Steps

After setup is complete:
1. Test the environment by deploying a small change
2. Verify data isolation by checking production is unaffected
3. Document team-specific procedures in your runbook
4. Set up monitoring and alerts for test environment (optional)
5. Configure automated tests to run against test environment (optional)

## Support

For issues or questions:
- See troubleshooting section in `docs/test-environment-setup.md`
- Check quick reference: `docs/test-environment-quick-reference.md`
- Review GitHub Actions logs for CI/CD issues
- Check Cloud Run service logs: `gcloud run services logs read pklnd-web-test`

## Conclusion

The test environment implementation is complete and ready to use. All documentation, scripts, and workflows are in place to support testing changes without affecting production.
