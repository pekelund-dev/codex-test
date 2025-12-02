# pklnd test environment (Terraform)

This module provisions an isolated copy of the pklnd Google Cloud footprint inside the same project by suffixing resource names with an environment label (default `test`). It creates a receipts bucket, dedicated Artifact Registry repositories, Cloud Run runtime service accounts (including an upload account), and placeholder Cloud Run services that use the public `gcr.io/cloudrun/hello` image until you deploy real builds. Production resources remain untouched.

## Usage

```bash
cd infra/test-environment
gcloud auth application-default login
terraform init
terraform apply -var "project_id=$(gcloud config get-value project)" \
  -var "region=europe-north1" \
  -var "env_name=test"
```

If the shared project already contains the test service accounts (for example, from a previous run), reuse them to avoid creati
on conflicts:

```bash
PROJECT_ID=$(gcloud config get-value project)

terraform apply -var "project_id=$PROJECT_ID" \
  -var "region=europe-north1" \
  -var "env_name=test" \
  -var "manage_service_accounts=false" \
  -var "web_service_account_email=cloud-run-runtime-test@$PROJECT_ID.iam.gserviceaccount.com" \
  -var "receipt_service_account_email=receipt-processor-test@$PROJECT_ID.iam.gserviceaccount.com" \
  -var "upload_service_account_email=receipt-uploads-test@$PROJECT_ID.iam.gserviceaccount.com"
```

After apply, use the outputs for deployment:

- `bucket_name` → pass to `GCS_BUCKET` when deploying the web app and receipt processor.
- `upload_service_account_email` → object admin for direct receipt uploads.
- `web_service_account_email` / `receipt_service_account_email` → runtime identities already attached to the placeholder Cloud Run services.
- `web_service_url` / `receipt_service_url` → initial endpoints (updated automatically when you redeploy images).
- `web_repository` / `receipt_repository` → container registries for the web and receipt processor images.
- `config_secret` → single Secret Manager entry for all sensitive values. Store one JSON payload (for example `{"google_client_id":"...","google_client_secret":"...","ai_studio_api_key":"..."}`) after apply; the runtime accounts already have accessor roles and the deploy scripts will read this secret automatically.

## Teardown

Terraform will destroy all resources by default. Review the plan carefully before destroying a test environment, and consider targeting specific resources if you want to leave the placeholder Cloud Run services running while removing other components. The `env_name` must remain non-empty and cannot be `prod`/`production`.
