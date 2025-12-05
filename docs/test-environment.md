# Test environment for pklnd

This guide provisions a separate test copy of the pklnd infrastructure inside the same Google Cloud project and deploys both Cloud Run services to it. Resources are isolated with a fixed `test` suffix.

## Step 1: Provision infrastructure

Choose one path to create the bucket, Artifact Registry repositories, runtime service accounts, and placeholder Cloud Run services. For the widest use of Google Cloud's Always Free allowances, use a US region such as `us-central1`; the examples below default to that region.

### gcloud script

```bash
export PROJECT_ID=$(gcloud config get-value project)
export TEST_ENV_NAME=test   # fixed suffix used throughout the stack
export REGION=us-central1 # optional and recommended for free-tier coverage
./scripts/setup_test_env_gcloud.sh
```

The script enables the required APIs, creates a receipts bucket (`pklnd-receipts-test-${PROJECT_ID}`), and provisions dedicated service accounts and Artifact Registry repositories suffixed with the environment name.

Secrets are stored in Secret Manager so credentials are not baked into deployments. After the script runs, add secret versions (free tier covers typical usage):

```bash
gcloud secrets versions add pklnd-oauth-client-id-${TEST_ENV_NAME} --data-file=<(printf "%s" "$GOOGLE_CLIENT_ID")
gcloud secrets versions add pklnd-oauth-client-secret-${TEST_ENV_NAME} --data-file=<(printf "%s" "$GOOGLE_CLIENT_SECRET")
# Optional when using Gemini API keys for the receipt processor
gcloud secrets versions add pklnd-ai-studio-api-key-${TEST_ENV_NAME} --data-file=<(printf "%s" "$AI_STUDIO_API_KEY")
```

### Terraform

```bash
gcloud auth application-default login
cd infra/test-environment
terraform init
terraform apply -var "project_id=$(gcloud config get-value project)" \
  -var "bucket_name=pklnd-receipts" \
  -var "region=us-central1"

# Passing bucket_name keeps this command identical to production. Omit it if you
# prefer the default env-suffixed bucket name (pklnd-receipts-test-${project_id}).
```

Existing projects typically already have the runtime service accounts. Terraform now defaults to reusing them automatically because `manage_service_accounts` is `false` and the module derives the service account emails from the environment name and project ID. If you need Terraform to create fresh identities (for a brand-new test project), set `manage_service_accounts=true`:

```bash
PROJECT_ID=$(gcloud config get-value project)

terraform apply -var "project_id=$PROJECT_ID" \
  -var "region=us-central1" \
  -var "manage_service_accounts=true"
```

When your service accounts use different names, keep `manage_service_accounts=false` and pass the custom emails via the `*_service_account_email` variables.

> Passing `-var "protect_services=..."` is accepted for backward compatibility but has no effect on the current Terraform reso
urces.

Terraform applies the same resource layout as the gcloud helper and also stands up Cloud Run services using the public `gcr.io/cloudrun/hello` image so you can see the endpoints immediately. Use the outputs for bucket names, service accounts (including the upload account), repository IDs, and service URLs when deploying.

A single Secret Manager entry (`config_secret`) is created empty; add one version that stores a JSON object with all sensitive values after `apply`, for example:

```json
{"google_client_id":"...","google_client_secret":"...","ai_studio_api_key":"..."}
```

Cloud Run runtimes already have secret accessors, and the deploy scripts will read these values automatically, falling back to inline environment variables only when the secret is missing.

> **Safety note:** The test environment no longer applies deletion protection. Double-check your target resources when running `terraform destroy` so you do not remove services accidentally.

To tear the Terraform-managed test environment down, run a destroy with the same variables and an explicit override for protection:

```bash
cd infra/test-environment
terraform destroy -var "project_id=$(gcloud config get-value project)" \
  -var "region=us-central1"
```

## Step 2: Deploy the services

After infrastructure is in place, deploy both Cloud Run services to the test environment:

```bash
export PROJECT_ID=$(gcloud config get-value project)
export TEST_ENV_NAME=test
export REGION=us-central1
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
