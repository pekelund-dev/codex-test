# pklnd test environment (Terraform)

This module provisions an isolated copy of the pklnd Google Cloud footprint inside the same project by suffixing resource names with an environment label (default `test`). It creates a receipts bucket, dedicated Artifact Registry repositories, Cloud Run runtime service accounts (including an upload account), and placeholder Cloud Run services that use the public `gcr.io/cloudrun/hello` image until you deploy real builds. Production resources remain untouched.

## Usage

```bash
cd infra/test-environment
gcloud auth application-default login
terraform init
terraform apply -var "project_id=$(gcloud config get-value project)" \
  -var "region=europe-north1" \
  -var "env_name=test" \
  -var "protect_services=true"
```

After apply, use the outputs for deployment:

- `bucket_name` → pass to `GCS_BUCKET` when deploying the web app and receipt processor.
- `upload_service_account_email` → object admin for direct receipt uploads.
- `web_service_account_email` / `receipt_service_account_email` → runtime identities already attached to the placeholder Cloud Run services.
- `web_service_url` / `receipt_service_url` → initial endpoints (updated automatically when you redeploy images).
- `web_repository` / `receipt_repository` → container registries for the web and receipt processor images.
- `config_secret` → single Secret Manager entry for all sensitive values. Store one JSON payload (for example `{"google_client_id":"...","google_client_secret":"...","ai_studio_api_key":"..."}`) after apply; the runtime accounts already have accessor roles and the deploy scripts will read this secret automatically.

## Teardown

> **Safety net:** Cloud Run services honor `protect_services` as deletion protection. Leave it `true` during day-to-day applies and set it to `false` only when you are sure you want Terraform to remove the managed services. The `env_name` variable must also be non-empty and must not equal `prod`/`production`.

To remove the entire test stack that was created by this module, run a destroy with the same variables you applied with and ov
erride the protection flag:

```bash
cd infra/test-environment
terraform destroy -var "project_id=$(gcloud config get-value project)" \
  -var "region=europe-north1" \
  -var "env_name=test" \
  -var "protect_services=false"
```

This deletes the suffixed buckets, Artifact Registry repositories, service accounts, and the placeholder Cloud Run services. Production resources are untouched.
