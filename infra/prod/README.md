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

> The defaults assume you want to mirror the current production naming scheme. Override any of the variables in `variables.tf` if your existing resources use different names.

If the runtime service accounts already exist (for example, `cloud-run-runtime` and `receipt-processor`), reuse them instead of deleting them:

```bash
terraform apply \
  -var "project_id=$(gcloud config get-value project)" \
  -var "region=europe-north1" \
  -var "bucket_name=pklnd-receipts" \
  -var "manage_service_accounts=false" \
  -var "web_service_account_email=cloud-run-runtime@$(gcloud config get-value project).iam.gserviceaccount.com" \
  -var "receipt_service_account_email=receipt-processor@$(gcloud config get-value project).iam.gserviceaccount.com" \
  -var "upload_service_account_email=receipt-uploads@$(gcloud config get-value project).iam.gserviceaccount.com"
```

After apply, pull the outputs to feed the deployment scripts:

```bash
terraform output
terraform output -raw bucket_name
terraform output -raw web_service_name
terraform output -raw receipt_service_name
```

To double-check that Terraform created the placeholder Cloud Run services, describe them with `gcloud` (they initially point to the public `gcr.io/cloudrun/hello` image):

```bash
WEB_SERVICE=$(terraform output -raw web_service_name)
RECEIPT_SERVICE=$(terraform output -raw receipt_service_name)
PROJECT_ID=$(terraform output -raw project_id)
REGION=$(terraform output -raw region)

gcloud run services describe "$WEB_SERVICE" \
  --project "$PROJECT_ID" --region "$REGION" \
  --format="value(status.url,status.latestReadyRevisionName,template.spec.containers[0].image)"

gcloud run services describe "$RECEIPT_SERVICE" \
  --project "$PROJECT_ID" --region "$REGION" \
  --format="value(status.url,status.latestReadyRevisionName,template.spec.containers[0].image)"
```

Seeing valid URLs and the `gcr.io/cloudrun/hello` image confirms the services exist and are ready for a production deployment with `scripts/deploy_prod_env.sh`.

One Secret Manager entry (`config_secret`) holds all sensitive values. Add a single JSON payload (for example `{"google_client_id":"...","google_client_secret":"...","ai_studio_api_key":"..."}`) with `gcloud secrets versions add ... --data-file=...` once; the runtime service accounts already have accessor roles and the deploy scripts will automatically read this secret instead of inline environment variables.

## Teardown

Terraform applies standard destroy behavior to all resources. Review the plan carefully before confirming a destroy in production, and consider targeting specific resources if you need to keep Cloud Run services online while removing other infrastructure.
