#!/usr/bin/env bash
set -euo pipefail

# This script provisions and deploys the web application to Cloud Run.
# Configure the environment variables below before running the script.

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-europe-north1}"
SERVICE_NAME="${SERVICE_NAME:-pklnd-web}"
SA_NAME="${SA_NAME:-cloud-run-runtime}"
ARTIFACT_REPO="${ARTIFACT_REPO:-web}"
# Firestore is shared between the Cloud Run app, the Cloud Function and the user registration flow.
# Default the shared project to the deployment project, but allow overrides when the
# Firestore database lives in a different project.
SHARED_FIRESTORE_PROJECT_ID="${SHARED_FIRESTORE_PROJECT_ID:-${PROJECT_ID}}"
# Append the shared Firestore project ID to the Cloud Run environment variables so the
# application resolves users from the same database that stores receipt extractions.
ENV_VARS="${ENV_VARS:-SPRING_PROFILES_ACTIVE=prod}"
if [[ -n "${SHARED_FIRESTORE_PROJECT_ID}" ]]; then
  if [[ -z "${ENV_VARS}" ]]; then
    ENV_VARS="FIRESTORE_PROJECT_ID=${SHARED_FIRESTORE_PROJECT_ID}"
  elif [[ "${ENV_VARS}" != *"FIRESTORE_PROJECT_ID="* ]]; then
    ENV_VARS="${ENV_VARS},FIRESTORE_PROJECT_ID=${SHARED_FIRESTORE_PROJECT_ID}"
  fi
fi
ALLOW_UNAUTH="${ALLOW_UNAUTH:-true}"
DOMAIN="${DOMAIN:-pklnd.pekelund.dev}"

if [[ -z "$PROJECT_ID" ]]; then
  echo "PROJECT_ID must be set" >&2
  exit 1
fi

set -x

gcloud config set project "$PROJECT_ID"

# Enable core APIs
 gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  firestore.googleapis.com \
  secretmanager.googleapis.com

# Create Artifact Registry repository (ignore error if it exists)
gcloud artifacts repositories create "$ARTIFACT_REPO" \
  --repository-format=docker \
  --location="$REGION" \
  --description="Container images for Cloud Run" || true

# Create Firestore database if missing (ignore error when already provisioned)
gcloud firestore databases create \
  --region="$REGION" \
  --type=firestore-native || true

# Create service account
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
if ! gcloud iam service-accounts describe "$SA_EMAIL" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$SA_NAME" \
    --project "$PROJECT_ID" \
    --description "Runtime SA for Cloud Run" \
    --display-name "Cloud Run Runtime"
fi

# Grant required roles
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/run.invoker" --condition=None || true

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/datastore.user" --condition=None || true

# Add Secret Manager role only if secrets are required; safe to attempt.
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/secretmanager.secretAccessor" --condition=None || true

# Build and push the image
IMAGE_URI="${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPO}/${SERVICE_NAME}:$(date +%Y%m%d-%H%M%S)"

gcloud builds submit \
  --tag "$IMAGE_URI"

# Deploy to Cloud Run
ALLOW_FLAG="--no-allow-unauthenticated"
if [[ "$ALLOW_UNAUTH" == "true" ]]; then
  ALLOW_FLAG="--allow-unauthenticated"
fi

gcloud run deploy "$SERVICE_NAME" \
  --image "$IMAGE_URI" \
  --service-account "$SA_EMAIL" \
  --region "$REGION" \
  --platform managed \
  $ALLOW_FLAG \
  --set-env-vars "${ENV_VARS}" \
  --min-instances 0 \
  --max-instances 10

# Configure custom domain mapping
gcloud beta run domain-mappings create \
  --service "$SERVICE_NAME" \
  --domain "$DOMAIN" \
  --region "$REGION" || true

gcloud beta run domain-mappings describe "$DOMAIN" \
  --region "$REGION"

# Display service URL and tail recent logs
SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" \
  --region "$REGION" \
  --format "value(status.url)")

echo "Service deployed: ${SERVICE_URL}"

gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=${SERVICE_NAME}" \
  --limit 20 \
  --format="table(timestamp, textPayload)"
