# pklnd production environment (Terraform)

This configuration provisions the production-grade pklnd footprint in Google Cloud with Terraform. It creates the receipts bucket, Artifact Registry repositories, runtime service accounts (including an upload account), and Cloud Run services for the web app and receipt processor. By default, resources match the names used by the existing deployment scripts (`pklnd-web`, `pklnd-receipts`, `cloud-run-runtime`, `receipt-processor`, `receipt-uploads`, and the `web`/`receipts` repositories).

## Usage

```bash
cd infra/prod
gcloud auth application-default login
terraform init
terraform apply \
  -var "project_id=$(gcloud config get-value project)" \
  -var "region=europe-north1" \
  -var "bucket_name=pklnd-receipts"
```

> The defaults assume you want to mirror the current production naming scheme. Override any of the variables in `variables.tf` if your existing resources use different names. Import pre-existing resources into the state before applying if they already exist.

After apply, pull the outputs to feed the deployment scripts:

```bash
terraform output
terraform output -raw bucket_name
terraform output -raw web_service_name
terraform output -raw receipt_service_name
```

One Secret Manager entry (`config_secret`) holds all sensitive values. Add a single JSON payload (for example `{"google_client_id":"...","google_client_secret":"...","ai_studio_api_key":"..."}`) with `gcloud secrets versions add ... --data-file=...` once; the runtime service accounts already have accessor roles and the deploy scripts will automatically read this secret instead of inline environment variables.

## Teardown

Terraform applies standard destroy behavior to all resources. Review the plan carefully before confirming a destroy in production, and consider targeting specific resources if you need to keep Cloud Run services online while removing other infrastructure.
