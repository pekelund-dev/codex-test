# Production deployment with Terraform

Use this guide to provision and deploy the production pklnd environment with Terraform and the helper scripts. It mirrors the existing names used by the deploy scripts (`pklnd-web`, `pklnd-receipts`, `cloud-run-runtime`, `receipt-processor`, `receipt-uploads`, and the `web`/`receipts` Artifact Registry repositories).

## Step 1: Provision infrastructure

Run Terraform from `infra/prod` to enable required APIs, create the storage bucket, repositories, service accounts, Secret Manager entries, and placeholder Cloud Run services:

```bash
cd infra/prod
gcloud auth application-default login
terraform init
terraform apply \
  -var "project_id=$(gcloud config get-value project)" \
  -var "region=europe-north1" \
  -var "bucket_name=pklnd-receipts"
```

If the runtime service accounts already exist in the project, reuse them instead of deleting them:

```bash
PROJECT_ID=$(gcloud config get-value project)

terraform apply \
  -var "project_id=$PROJECT_ID" \
  -var "region=europe-north1" \
  -var "bucket_name=pklnd-receipts" \
  -var "manage_service_accounts=false" \
  -var "web_service_account_email=cloud-run-runtime@$PROJECT_ID.iam.gserviceaccount.com" \
  -var "receipt_service_account_email=receipt-processor@$PROJECT_ID.iam.gserviceaccount.com" \
  -var "upload_service_account_email=receipt-uploads@$PROJECT_ID.iam.gserviceaccount.com"
```

> Passing `-var "protect_services=..."` is accepted for backward compatibility but has no effect on the current Terraform reso
urces.

After apply, note the outputs for bucket name, Artifact Registry repositories, service accounts, and the `config_secret` entry. Add a single secret version that stores your sensitive values as JSON so you stay within the Secret Manager free tier:

```json
{"google_client_id":"...","google_client_secret":"...","ai_studio_api_key":"..."}
```

Then upload it once:

```bash
gcloud secrets versions add pklnd-config --data-file=/path/to/config.json
```

The Secret Manager free tier covers typical access volume, keeping costs negligible. The Terraform module already grants the runtime service accounts accessor roles.

> The defaults match the current production naming scheme. Override values in `variables.tf` if your existing resources use different identifiers. Import any pre-existing resources before you apply so Terraform manages them safely.

### Verify Cloud Run scaffolding before deploying

Terraform seeds placeholder Cloud Run services using the public `gcr.io/cloudrun/hello` image so you can confirm everything exists before pushing real images. Use the outputs to describe both services:

```bash
cd infra/prod
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

You should see a URL for each service and the `gcr.io/cloudrun/hello` image until you deploy the production artifacts with `deploy_prod_env.sh`.

## Step 2: Deploy the services

With infrastructure in place, deploy the production builds via the helper script. It reuses Terraform outputs when available and falls back to the standard production names:

```bash
export PROJECT_ID=$(gcloud config get-value project)
export REGION=europe-north1
export GOOGLE_CLIENT_ID=your-client-id
export GOOGLE_CLIENT_SECRET=your-client-secret
# Optional: export GOOGLE_OAUTH_CREDENTIALS_FILE if your OAuth client lives in JSON form.
./scripts/deploy_prod_env.sh
```

The script builds and deploys both Cloud Run services, attaching the production service accounts and pointing the web app at the receipt processor URL. Set `SPRING_PROFILES_ACTIVE` if you need a custom profile list (defaults to `prod`).

## Teardown controls

The Terraform configuration no longer applies deletion protection to Cloud Run services. Use `terraform destroy` with care in production environments and consider removing the services from your target list if you want to keep them running while tearing down other resources.
