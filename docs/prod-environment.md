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
  -var "bucket_name=pklnd-receipts" \
  -var "protect_services=true"
```

After apply, note the outputs for bucket name, Artifact Registry repositories, service accounts, and the Secret Manager entries (`oauth_client_id_secret`, `oauth_client_secret_secret`, `ai_studio_api_key_secret`). Add secret versions once with low-cost Secret Manager storage so Cloud Run can read them at runtime:

```bash
gcloud secrets versions add pklnd-oauth-client-id --data-file=<(printf "%s" "$GOOGLE_CLIENT_ID")
gcloud secrets versions add pklnd-oauth-client-secret --data-file=<(printf "%s" "$GOOGLE_CLIENT_SECRET")
# Optional Gemini/AI Studio key for the receipt processor
gcloud secrets versions add pklnd-ai-studio-api-key --data-file=<(printf "%s" "$AI_STUDIO_API_KEY")
```

The Secret Manager free tier covers typical access volume, keeping costs negligible. The Terraform module already grants the runtime service accounts accessor roles.

> The defaults match the current production naming scheme. Override values in `variables.tf` if your existing resources use different identifiers. Import any pre-existing resources before you apply so Terraform manages them safely.

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

The Terraform configuration uses `protect_services` to prevent accidental deletion of production resources. Leave it `true` during normal operation. Only set it to `false` if you deliberately intend to run `terraform destroy` and remove the managed Cloud Run services, bucket, repositories, and service accounts.
