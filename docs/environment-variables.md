# Deployment Environment Variables

This quick-reference lists every environment variable consumed by the automated deployment
scripts. Copy the appropriate block into your shell profile or `.env` files and adjust the
values for your Google Cloud projects.

> **Prefixing convention**
>
> Use `PROD_` or `STAGING_` in front of any variable name to scope it to that environment
> when invoking the shared deployment scripts. For example, set `STAGING_PROJECT_ID` to point
> staging deployments at a different Google Cloud project without overwriting the production
> value in your shell session.

---

## Web application (`scripts/deploy_cloud_run.sh`)

```bash
# Shared base configuration
PROD_PROJECT_ID="pklnd-prod-project"
PROD_REGION="europe-north1"
PROD_GCS_BUCKET="pklnd-receipts"
PROD_GOOGLE_CLIENT_ID="<google-oauth-client-id>"
PROD_GOOGLE_CLIENT_SECRET="<google-oauth-client-secret>"
PROD_SERVICE_NAME="pklnd-web"
PROD_DOMAIN="pklnd.pekelund.dev"
PROD_SHARED_INFRA_PROJECT_ID="$PROD_PROJECT_ID"

STAGING_PROJECT_ID="pklnd-test-project"
STAGING_REGION="europe-north1"
STAGING_GCS_BUCKET="pklnd-receipts"
STAGING_GOOGLE_CLIENT_ID="<google-oauth-client-id>"
STAGING_GOOGLE_CLIENT_SECRET="<google-oauth-client-secret>"
STAGING_SERVICE_NAME="pklnd-web-test"
STAGING_DOMAIN="test.pkelund.dev"
STAGING_SHARED_INFRA_PROJECT_ID="$PROD_PROJECT_ID"  # reuse production Firestore/GCS by default
```

| Variable | Description |
| --- | --- |
| `PROJECT_ID` | Google Cloud project that hosts the Cloud Run service and Artifact Registry. |
| `REGION` | Cloud Run region (default `europe-north1`). |
| `SERVICE_NAME` | Cloud Run service name (`pklnd-web` in prod, `pklnd-web-test` in staging). |
| `DOMAIN` | Custom domain mapping applied after deployment. |
| `SA_NAME` | Runtime service account name (`cloud-run-runtime` by default). |
| `ARTIFACT_REPO` | Artifact Registry Docker repository name (`web`). |
| `BUILD_CONTEXT` | Directory passed to `gcloud builds submit` (default repository root). |
| `SHARED_INFRA_PROJECT_ID` | Project that owns shared Firestore/GCS infrastructure. |
| `SHARED_FIRESTORE_PROJECT_ID` | Override Firestore project if different from shared infra project. |
| `SHARED_GCS_PROJECT_ID` | Override GCS project if different from shared infra project. |
| `SPRING_PROFILES_ACTIVE` | Spring profile to activate (`prod` or `staging`; script appends `oauth`). |
| `FIRESTORE_ENABLED` | Toggle Firestore integration (default `true`). |
| `GOOGLE_CLIENT_ID` | OAuth 2.0 client ID. |
| `GOOGLE_CLIENT_SECRET` | OAuth 2.0 client secret. |
| `GOOGLE_OAUTH_CREDENTIALS_FILE` | Optional path to a JSON client secret file. |
| `GCS_ENABLED` | Toggle Cloud Storage integration (default `true`). |
| `GCS_BUCKET` | Cloud Storage bucket that stores receipt uploads. |
| `GCS_CREDENTIALS` | Optional base64-encoded credentials for non-GCP environments. |
| `RECEIPT_PROCESSOR_BASE_URL` | URL of the receipt processor Cloud Run service. |
| `RECEIPT_PROCESSOR_AUDIENCE` | Audience claim for signed invocations to the receipt processor. |
| `ALLOW_UNAUTH` | Pass `true` to allow unauthenticated access (default `true`). |
| `IMAGE_URI` | Existing container image to deploy instead of building a new one. |
| `IMAGE_RESOURCE` | Base Artifact Registry path; script derives from other values. |

---

## Receipt processor (`scripts/deploy_receipt_processor.sh`)

```bash
PROD_PROJECT_ID="pklnd-prod-project"
PROD_REGION="europe-north1"
PROD_GCS_BUCKET="pklnd-receipts"
PROD_SERVICE_NAME="pklnd-receipts"
PROD_SA_NAME="receipt-processor"
PROD_ARTIFACT_REPO="receipts"
PROD_WEB_SERVICE_NAME="pklnd-web"
PROD_WEB_SERVICE_REGION="europe-north1"

STAGING_PROJECT_ID="pklnd-test-project"
STAGING_REGION="europe-north1"
STAGING_GCS_BUCKET="pklnd-receipts"
STAGING_SERVICE_NAME="pklnd-receipts-test"
STAGING_SA_NAME="receipt-processor-test"
STAGING_ARTIFACT_REPO="receipts"
STAGING_WEB_SERVICE_NAME="pklnd-web-test"
STAGING_WEB_SERVICE_REGION="europe-north1"
```

| Variable | Description |
| --- | --- |
| `PROJECT_ID` | Google Cloud project for the receipt processor deployment. |
| `REGION` | Cloud Run region for the service (default `europe-north1`). |
| `SERVICE_NAME` | Cloud Run service name (`pklnd-receipts` / `pklnd-receipts-test`). |
| `SA_NAME` | Receipt processor runtime service account. |
| `ARTIFACT_REPO` | Artifact Registry repository that stores receipt processor images. |
| `BUILD_CONTEXT` | Build root passed to Cloud Build (default repository root). |
| `DOCKERFILE_PATH` | Dockerfile path relative to repository root (`receipt-parser/Dockerfile`). |
| `CLOUD_BUILD_CONFIG` | Cloud Build configuration (`receipt-parser/cloudbuild.yaml`). |
| `GCS_BUCKET` | Receipt uploads bucket (must match web application configuration). |
| `WEB_SERVICE_NAME` | Web frontend service that triggers the processor. |
| `WEB_SERVICE_REGION` | Region of the web frontend service. |
| `WEB_SERVICE_ACCOUNT` | Service account allowed to invoke the processor (auto-detected). |
| `ADDITIONAL_INVOKER_SERVICE_ACCOUNTS` | Comma-separated list of extra service accounts with `roles/run.invoker`. |
| `VERTEX_AI_PROJECT_ID` | Project hosting Vertex AI (defaults to deployment project). |
| `VERTEX_AI_LOCATION` | Vertex AI region (defaults to deployment region). |
| `VERTEX_AI_GEMINI_MODEL` | Gemini model name (`gemini-2.0-flash`). |
| `RECEIPT_FIRESTORE_COLLECTION` | Firestore collection for parsed receipts (`receiptExtractions`). |
| `RECEIPT_FIRESTORE_ITEM_COLLECTION` | Firestore collection for receipt line items (`receiptItems`). |
| `RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION` | Firestore collection for aggregate stats (`receiptItemStats`). |
| `SPRING_PROFILES_ACTIVE` | Spring profile (`prod` or `staging`; script injects `APP_ENVIRONMENT`). |
| `LOGGING_PROJECT_ID` | Destination project for structured logs (defaults to deployment project). |
| `AI_STUDIO_API_KEY` | Optional API key for local Gemini evaluation endpoints. |

---

## Local secrets helpers

When running locally, point to credential files and let the helper scripts translate them
into runtime variables:

```bash
export FIRESTORE_CREDENTIALS_FILE="$HOME/.config/pklnd/firestore.json"
export GOOGLE_OAUTH_CREDENTIALS_FILE="$HOME/.config/pklnd/oauth-client.json"
source ./scripts/load_local_secrets.sh
```

The Cloud Run deployment scripts automatically read `GOOGLE_OAUTH_CREDENTIALS_FILE` and
extract `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` when the explicit variables are not set.

---

## Usage examples

```bash
# Deploy staging web service with its scoped variables
./scripts/deploy_cloud_run_test.sh

# Promote the verified image to production using the stored PROD_ values
IMAGE_URI="$STAGING_IMAGE_URI" ./scripts/deploy_cloud_run.sh --env prod

# Deploy the receipt processor into staging
./scripts/deploy_receipt_processor_test.sh
```

Copy the relevant block, replace the placeholders, and keep both staging and production
values side by side for easy promotion workflows.
