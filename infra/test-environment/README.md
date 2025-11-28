# pklnd test environment (Terraform)

This module provisions an isolated copy of the pklnd Google Cloud footprint inside the same project by suffixing resource names with an environment label (default `test`). It creates dedicated Artifact Registry repositories, Cloud Run runtime service accounts, and a receipts bucket without touching your production resources.

## Usage

```bash
cd infra/test-environment
gcloud auth application-default login
terraform init
terraform apply -var "project_id=$(gcloud config get-value project)" \
  -var "region=europe-north1" \
  -var "env_name=test"
```

After apply, use the outputs for deployment:

- `bucket_name` → pass to `GCS_BUCKET` when deploying the web app and receipt processor.
- `web_service_account_email` / `receipt_service_account_email` → attach to the corresponding Cloud Run services.
- `web_repository` / `receipt_repository` → container registries for the web and receipt processor images.
