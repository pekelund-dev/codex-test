#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-europe-north1}"
SERVICE_NAME="${RECEIPT_SERVICE_NAME:-pklnd-receipts}"
SA_NAME="${RECEIPT_SA_NAME:-receipt-processor}"
ARTIFACT_REPO="${RECEIPT_ARTIFACT_REPO:-receipts}"
TRIGGER_NAME="${RECEIPT_TRIGGER_NAME:-receipt-processing-trigger}"
DESTINATION_RUN_PATH="${RECEIPT_EVENT_PATH:-/events/storage}"
GCS_BUCKET="${GCS_BUCKET:-}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured with 'gcloud config set project'." >&2
  exit 1
fi

if [[ -z "${GCS_BUCKET}" ]]; then
  echo "GCS_BUCKET must be provided so Eventarc can subscribe to finalize events." >&2
  exit 1
fi

VERTEX_AI_PROJECT_ID="${VERTEX_AI_PROJECT_ID:-${PROJECT_ID}}"
VERTEX_AI_LOCATION="${VERTEX_AI_LOCATION:-${REGION}}"
VERTEX_AI_GEMINI_MODEL="${VERTEX_AI_GEMINI_MODEL:-gemini-2.0-flash}"
RECEIPT_FIRESTORE_PROJECT_ID="${RECEIPT_FIRESTORE_PROJECT_ID:-${PROJECT_ID}}"
RECEIPT_FIRESTORE_COLLECTION="${RECEIPT_FIRESTORE_COLLECTION:-receiptExtractions}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

append_env_var() {
  local key="$1"
  local value="$2"

  if [[ -z "$value" ]]; then
    return
  fi

  local pair="${key}=${value}"
  local i
  for i in "${!ENV_VARS_LIST[@]}"; do
    if [[ "${ENV_VARS_LIST[i]}" == "${key}="* ]]; then
      ENV_VARS_LIST[i]="${pair}"
      return
    fi
  done

  ENV_VARS_LIST+=("${pair}")
}

ENV_VARS_LIST=()
append_env_var "SPRING_PROFILES_ACTIVE" "$SPRING_PROFILES_ACTIVE"
append_env_var "VERTEX_AI_PROJECT_ID" "$VERTEX_AI_PROJECT_ID"
append_env_var "VERTEX_AI_LOCATION" "$VERTEX_AI_LOCATION"
append_env_var "VERTEX_AI_GEMINI_MODEL" "$VERTEX_AI_GEMINI_MODEL"
append_env_var "RECEIPT_FIRESTORE_PROJECT_ID" "$RECEIPT_FIRESTORE_PROJECT_ID"
append_env_var "RECEIPT_FIRESTORE_COLLECTION" "$RECEIPT_FIRESTORE_COLLECTION"
append_env_var "LOGGING_PROJECT_ID" "${LOGGING_PROJECT_ID:-${PROJECT_ID}}"

choose_env_delimiter() {
  local candidates=("|" "@" ":" ";" "#" "+" "~" "^" "%" "?")
  local candidate pair
  for candidate in "${candidates[@]}"; do
    local collision=false
    for pair in "${ENV_VARS_LIST[@]}"; do
      if [[ "$pair" == *"${candidate}"* ]]; then
        collision=true
        break
      fi
    done
    if [[ "$collision" == false ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  echo "Failed to determine delimiter for Cloud Run environment variables" >&2
  exit 1
}

ENV_VARS_ARG=""
if (( ${#ENV_VARS_LIST[@]} > 0 )); then
  DELIM="$(choose_env_delimiter)"
  ENV_VARS_ARG="^${DELIM}^$(IFS="$DELIM"; printf '%s' "${ENV_VARS_LIST[*]}")"
fi

BUCKET_REGION=$(gcloud storage buckets describe "gs://${GCS_BUCKET}" --format="value(location)" 2>/dev/null || true)
if [[ -z "$BUCKET_REGION" ]]; then
  echo "Unable to determine region for bucket gs://${GCS_BUCKET}" >&2
  exit 1
fi
REGION=$(echo "$REGION" | tr '[:upper:]' '[:lower:]')
BUCKET_REGION_LOWER=$(echo "$BUCKET_REGION" | tr '[:upper:]' '[:lower:]')
if [[ "$REGION" != "$BUCKET_REGION_LOWER" ]]; then
  echo "Using Cloud Run region ${REGION}, bucket is in ${BUCKET_REGION_LOWER}. Eventarc supports cross-region delivery but consider aligning regions for latency." >&2
fi

set -x

gcloud config set project "$PROJECT_ID"

gcloud services enable \
  run.googleapis.com \
  eventarc.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  firestore.googleapis.com \
  storage.googleapis.com \
  pubsub.googleapis.com \
  aiplatform.googleapis.com \
  --quiet

if ! gcloud artifacts repositories describe "$ARTIFACT_REPO" --location="$REGION" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$ARTIFACT_REPO" \
    --repository-format=docker \
    --location="$REGION" \
    --description="Container images for receipt processor"
fi

if ! gcloud firestore databases describe --database="(default)" --project="$RECEIPT_FIRESTORE_PROJECT_ID" --format="value(name)" >/dev/null 2>&1; then
  gcloud firestore databases create \
    --location="$REGION" \
    --type=firestore-native \
    --project="$RECEIPT_FIRESTORE_PROJECT_ID"
fi

if ! gcloud iam service-accounts describe "$SA_EMAIL" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$SA_NAME" \
    --project "$PROJECT_ID" \
    --description "Receipt processor runtime" \
    --display-name "Receipt Processor"
fi

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/run.invoker" --condition=None || true

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/datastore.user" --condition=None || true

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/aiplatform.user" --condition=None || true

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/eventarc.eventReceiver" --condition=None || true

gcloud storage buckets add-iam-policy-binding "gs://${GCS_BUCKET}" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/storage.objectAdmin" \
  --quiet || true

IMAGE_RESOURCE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPO}/${SERVICE_NAME}"
IMAGE_URI="${IMAGE_RESOURCE}:$(date +%Y%m%d-%H%M%S)"

gcloud builds submit \
  --tag "$IMAGE_URI"

gcloud run deploy "$SERVICE_NAME" \
  --image "$IMAGE_URI" \
  --service-account "$SA_EMAIL" \
  --region "$REGION" \
  --platform managed \
  --min-instances 0 \
  --max-instances 5 \
  --set-env-vars "${ENV_VARS_ARG}" \
  --no-allow-unauthenticated

gcloud run services add-iam-policy-binding "$SERVICE_NAME" \
  --region "$REGION" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/run.invoker" \
  --quiet || true

PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format="value(projectNumber)")
EVENTARC_AGENT="service-${PROJECT_NUMBER}@gcp-sa-eventarc.iam.gserviceaccount.com"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${EVENTARC_AGENT}" \
  --role "roles/eventarc.eventReceiver" \
  --quiet || true

gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
  --member "serviceAccount:${EVENTARC_AGENT}" \
  --role "roles/iam.serviceAccountTokenCreator" \
  --project "$PROJECT_ID" \
  --quiet || true

if gcloud eventarc triggers describe "$TRIGGER_NAME" --location "$REGION" >/dev/null 2>&1; then
  gcloud eventarc triggers update "$TRIGGER_NAME" \
    --location "$REGION" \
    --destination-run-service "$SERVICE_NAME" \
    --destination-run-region "$REGION" \
    --destination-run-path "$DESTINATION_RUN_PATH" \
    --event-filters type=google.cloud.storage.object.v1.finalized \
    --event-filters bucket="$GCS_BUCKET" \
    --service-account "$SA_EMAIL"
else
  gcloud eventarc triggers create "$TRIGGER_NAME" \
    --location "$REGION" \
    --destination-run-service "$SERVICE_NAME" \
    --destination-run-region "$REGION" \
    --destination-run-path "$DESTINATION_RUN_PATH" \
    --event-filters type=google.cloud.storage.object.v1.finalized \
    --event-filters bucket="$GCS_BUCKET" \
    --service-account "$SA_EMAIL"
fi

SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" --region "$REGION" --format "value(status.url)")

echo "Receipt processor deployed: ${SERVICE_URL}"

gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=${SERVICE_NAME}" \
  --limit 20 \
  --format="table(timestamp, textPayload)"

set +x

echo "✅ Receipt processor deployment complete."
