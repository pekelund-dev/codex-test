# Terraform deployment

The Terraform workflow provisions all Google Cloud resources and deploys both Cloud Run services while consolidating secrets into a single Secret Manager secret. Infrastructure creation is separate from application deployment so you can iterate on code without reprovisioning foundational services.

## Prerequisites

- [Terraform](https://developer.hashicorp.com/terraform/downloads) 1.5 or newer
- [gcloud CLI](https://cloud.google.com/sdk/docs/install) authenticated against your project (`gcloud auth login`)
- Billing-enabled Google Cloud project ID exported as `PROJECT_ID`
- OAuth client credentials stored in a JSON file with the following structure:

```json
{"google_client_id": "your-client-id", "google_client_secret": "your-client-secret", "ai_studio_api_key": ""}
```

> Keep credential files out of version control. The scripts below read them locally and create a single Secret Manager secret that is shared by both Cloud Run services.

## Provision infrastructure

Run the helper from the repository root to enable core APIs, create service accounts, provision Artifact Registry repositories, a Firestore database, the receipts bucket, and the unified Secret Manager secret.

```bash
APP_SECRET_FILE=/path/to/pklnd-secret.json \
PROJECT_ID=$PROJECT_ID \
REGION=us-east1 \  # Optional override
./scripts/terraform/apply_infrastructure.sh
```

Key variables (override via environment variables):

- `PROJECT_ID` – target Google Cloud project
- `REGION` / `FIRESTORE_LOCATION` – defaults to `us-east1`
- `FIRESTORE_DATABASE_NAME` – defaults to `receipts-db`; set to `(default)` if you prefer the primary database id
- `BUCKET_NAME` – defaults to `pklnd-receipts-<project>`
- `APP_SECRET_NAME` – Secret Manager id to create or update (defaults to `pklnd-app-config`)

If `APP_SECRET_FILE` is omitted, the secret is created without an initial version so you can upload credentials manually with `gcloud secrets versions add` later.

> Using a named database such as `receipts-db` is valid with Firestore in Native mode. If you prefer to stick with the implicit primary database, set `FIRESTORE_DATABASE_NAME="(default)"` before running the scripts. Make sure the application configuration points to the same database id you provision.

## Deploy services

After infrastructure is in place and the secret contains your OAuth credentials, deploy the web and receipt-processor services through the Terraform deployment stack. The helper builds both container images with Cloud Build before applying the configuration.

```bash
PROJECT_ID=$PROJECT_ID \
REGION=us-east1 \  # Optional override
APP_SECRET_NAME=pklnd-app-config \  # Optional override
./scripts/terraform/deploy_services.sh
```

The script automatically:

- Reads Terraform outputs to reuse the generated service accounts, bucket, and Artifact Registry repositories
- Builds timestamped container images for both services and pushes them to Artifact Registry
- Pulls `google_client_id`, `google_client_secret`, and `ai_studio_api_key` from the single Secret Manager secret
- Applies the Cloud Run services and IAM bindings

Terraform does not fully support Cloud Run v2 domain mappings. After `terraform apply` completes, create the mapping manually if you need a custom domain. The commands below pull the service name and region from Terraform outputs; set `CUSTOM_DOMAIN` to your own domain before running the command:

```bash
WEB_SERVICE_NAME=$(terraform -chdir=infra/terraform/deployment output -raw web_service_name)
REGION=$(terraform -chdir=infra/terraform/deployment output -raw region)
# Set to your desired domain
CUSTOM_DOMAIN=pklnd.pekelund.dev

gcloud beta run domain-mappings create \
  --service "$WEB_SERVICE_NAME" \
  --domain "$CUSTOM_DOMAIN" \
  --region "$REGION"
```

If you need to move the domain to a different service, delete the mapping first and recreate it with the new service name:

```bash
gcloud beta run domain-mappings delete --domain $CUSTOM_DOMAIN --region $REGION
```

It can take 15–60 minutes for DNS propagation and SSL certificate provisioning to complete after creating or updating a domain mapping.

You can still override `WEB_SERVICE_NAME`/`RECEIPT_SERVICE_NAME` when you deploy multiple environments.

## Teardown

Destroy the deployment stack first, then remove the shared infrastructure when you want to clean up the project. The helper scripts will reuse your configured gcloud project and default to `us-east1` so they can run without extra flags:

```bash
scripts/terraform/destroy_services.sh
scripts/terraform/destroy_infrastructure.sh
```

## Legacy automation

The previous gcloud-based bash scripts are preserved under `scripts/legacy/` for reference. They still work for environments that were created before the Terraform migration, but new deployments should rely on the Terraform workflow above to keep secrets consolidated and infrastructure reproducible.
