# pklnd test environment (Terraform)

This module provisions an isolated copy of the pklnd Google Cloud footprint inside the same project by suffixing resource names with an environment label (default `test`). It creates a receipts bucket, dedicated Artifact Registry repositories, Cloud Run runtime service accounts (including an upload account), and placeholder Cloud Run services that use the public `gcr.io/cloudrun/hello` image until you deploy real builds. Production resources remain untouched.

To keep usage inside Google Cloud's Always Free allowances, prefer a US region such as `us-central1`. The defaults below use `us-central1`; change only for latency or residency requirements.

## Usage

```bash
cd infra/test-environment
gcloud auth application-default login
terraform init
terraform apply -var "project_id=$(gcloud config get-value project)" \
  -var "bucket_name=pklnd-receipts" \
  -var "region=us-central1"

# Passing bucket_name keeps the invocation aligned with production. Omit it if
# you want Terraform to use the default env-suffixed bucket name
# (pklnd-receipts-test-${project_id}).
```

Existing projects usually already contain the test service accounts. Terraform reuses them automatically now because `manage_service_accounts` defaults to `false` and derives the emails from the environment name and project ID. To have Terraform create new identities in a brand-new project, set `manage_service_accounts=true`:

```bash
PROJECT_ID=$(gcloud config get-value project)

terraform apply -var "project_id=$PROJECT_ID" \
  -var "region=us-central1" \
  -var "manage_service_accounts=true"
```

If your service accounts use different names, keep `manage_service_accounts=false` and pass the emails with the `*_service_account_email` variables.

Upload bindings are applied only when Terraform manages service accounts or when you supply an explicit `upload_service_account_email`. Omit that variable (and leave `manage_service_accounts=false`) if you do not have a dedicated upload identity; Terraform will skip the upload roles to avoid missing-account errors.

After apply, use the outputs for deployment:

- `bucket_name` → pass to `GCS_BUCKET` when deploying the web app and receipt processor.
- `upload_service_account_email` → object admin for direct receipt uploads.
- `web_service_account_email` / `receipt_service_account_email` → runtime identities already attached to the placeholder Cloud Run services.
- `web_service_url` / `receipt_service_url` → initial endpoints (updated automatically when you redeploy images).
- `web_repository` / `receipt_repository` → container registries for the web and receipt processor images.
- `config_secret` → single Secret Manager entry for all sensitive values. Store one JSON payload (for example `{"google_client_id":"...","google_client_secret":"...","ai_studio_api_key":"..."}`) after apply; the runtime accounts already have accessor roles and the deploy scripts will read this secret automatically.

## Teardown

Terraform will destroy all resources by default. Review the plan carefully before destroying a test environment, and consider targeting specific resources if you want to leave the placeholder Cloud Run services running while removing other components.
