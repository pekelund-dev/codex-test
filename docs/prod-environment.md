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

The Terraform configuration uses `protect_services` to enable Cloud Run deletion protection. Leave it `true` during normal operation. Set it to `false` only if you deliberately intend to let Terraform remove the managed services; other resources will follow the normal destroy plan.
