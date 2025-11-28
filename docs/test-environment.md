# Test environment for pklnd

This guide provisions a separate test copy of the pklnd infrastructure inside the same Google Cloud project and deploys both Cloud Run services to it. Resources are isolated with a configurable environment suffix (`TEST_ENV_NAME`, default `test`).

## Step 1: Provision infrastructure

Choose one path to create the bucket, Artifact Registry repositories, and runtime service accounts:

### gcloud script

```bash
export PROJECT_ID=$(gcloud config get-value project)
export TEST_ENV_NAME=test   # optional, defaults to "test"
export REGION=europe-north1 # optional
./scripts/setup_test_env_gcloud.sh
```

The script enables the required APIs, creates a receipts bucket (`pklnd-receipts-${TEST_ENV_NAME}-${PROJECT_ID}`), and provisions dedicated service accounts and Artifact Registry repositories suffixed with the environment name.

### Terraform

```bash
gcloud auth application-default login
cd infra/test-environment
terraform init
terraform apply -var "project_id=$(gcloud config get-value project)" \
  -var "region=europe-north1" \
  -var "env_name=test"
```

Terraform applies the same resource layout as the gcloud helper. Use the outputs for bucket names, service accounts, and repository IDs when deploying.

## Step 2: Deploy the services

After infrastructure is in place, deploy both Cloud Run services to the test environment:

```bash
export PROJECT_ID=$(gcloud config get-value project)
export TEST_ENV_NAME=test
export REGION=europe-north1
export GOOGLE_CLIENT_ID=your-client-id
export GOOGLE_CLIENT_SECRET=your-client-secret
# Optional overrides when OAuth credentials live in a JSON file
# export GOOGLE_OAUTH_CREDENTIALS_FILE=$HOME/.config/pklnd/oauth-client.json
./scripts/deploy_test_env.sh
```

The deploy script:

- Builds and deploys the receipt processor to `pklnd-receipts-${TEST_ENV_NAME}` using the `receipts-${TEST_ENV_NAME}` Artifact Registry repository.
- Deploys the web app to `pklnd-web-${TEST_ENV_NAME}` using the `web-${TEST_ENV_NAME}` repository.
- Points the web app at the test receipt processor URL via `RECEIPT_PROCESSOR_BASE_URL`.
- Reuses the same Firestore database but keeps buckets, registries, and service accounts separate via the environment suffix.

Both deploy helpers rely on the existing `deploy_cloud_run.sh` and `deploy_receipt_processor.sh` scripts, so you can supply the same optional variables (for example `SPRING_PROFILES_ACTIVE`, `VERTEX_AI_LOCATION`, or custom Dockerfile paths).
