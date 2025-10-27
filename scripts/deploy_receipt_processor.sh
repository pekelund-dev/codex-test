#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$REPO_ROOT"

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-europe-north1}"
SERVICE_NAME="${RECEIPT_SERVICE_NAME:-pklnd-receipts}"
SA_NAME="${RECEIPT_SA_NAME:-receipt-processor}"
ARTIFACT_REPO="${RECEIPT_ARTIFACT_REPO:-receipts}"
GCS_BUCKET="${GCS_BUCKET:-}"
ADDITIONAL_INVOKER_SERVICE_ACCOUNTS="${ADDITIONAL_INVOKER_SERVICE_ACCOUNTS:-}"
WEB_SERVICE_ACCOUNT="${WEB_SERVICE_ACCOUNT:-}"
WEB_SERVICE_NAME="${WEB_SERVICE_NAME:-pklnd-web}"
WEB_SERVICE_REGION="${WEB_SERVICE_REGION:-${REGION}}"
DOCKERFILE_PATH="${RECEIPT_DOCKERFILE:-receipt-parser/Dockerfile}"
BUILD_CONTEXT="${RECEIPT_BUILD_CONTEXT:-${REPO_ROOT}}"
CLOUD_BUILD_CONFIG="${RECEIPT_CLOUD_BUILD_CONFIG:-receipt-parser/cloudbuild.yaml}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured with 'gcloud config set project'." >&2
  exit 1
fi

if [[ -z "${WEB_SERVICE_ACCOUNT}" ]]; then
  detected_service_account=$(gcloud run services describe "${WEB_SERVICE_NAME}" \
    --region "${WEB_SERVICE_REGION}" \
    --project "${PROJECT_ID}" \
    --format "value(spec.template.spec.serviceAccount)" 2>/dev/null || true)

  if [[ -n "${detected_service_account}" && "${detected_service_account}" != "-" ]]; then
    WEB_SERVICE_ACCOUNT="${detected_service_account}"
    echo "Detected web runtime service account ${WEB_SERVICE_ACCOUNT} from ${WEB_SERVICE_NAME} in ${WEB_SERVICE_REGION}."
  fi
fi

if [[ -z "${WEB_SERVICE_ACCOUNT}" ]]; then
  WEB_SERVICE_ACCOUNT="cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
  echo "Defaulting WEB_SERVICE_ACCOUNT to ${WEB_SERVICE_ACCOUNT}."
fi

if [[ -z "${GCS_BUCKET}" ]]; then
  echo "GCS_BUCKET must be provided so the service account can access uploaded receipts." >&2
  exit 1
fi

if [[ ! -f "${DOCKERFILE_PATH}" ]]; then
  echo "Receipt processor Dockerfile not found at ${DOCKERFILE_PATH}. Set RECEIPT_DOCKERFILE when using a custom location." >&2
  exit 1
fi

if [[ ! -d "${BUILD_CONTEXT}" ]]; then
  echo "Receipt processor build context not found at ${BUILD_CONTEXT}. Set RECEIPT_BUILD_CONTEXT when using a custom directory." >&2
  exit 1
fi

if [[ ! -f "${CLOUD_BUILD_CONFIG}" ]]; then
  echo "Cloud Build configuration not found at ${CLOUD_BUILD_CONFIG}. Set RECEIPT_CLOUD_BUILD_CONFIG when using a custom file." >&2
  exit 1
fi

DOCKERFILE_ABS=$(DOCKERFILE_PATH="${DOCKERFILE_PATH}" python3 - <<'PY'
import os
from pathlib import Path

dockerfile = Path(os.environ["DOCKERFILE_PATH"]).expanduser().resolve()
print(dockerfile)
PY
)

BUILD_CONTEXT_ABS=$(BUILD_CONTEXT="${BUILD_CONTEXT}" python3 - <<'PY'
import os
from pathlib import Path

context = Path(os.environ["BUILD_CONTEXT"]).expanduser().resolve()
print(context)
PY
)

if [[ "${DOCKERFILE_ABS}" != "${BUILD_CONTEXT_ABS}"* ]]; then
  echo "Dockerfile ${DOCKERFILE_PATH} must reside inside the build context ${BUILD_CONTEXT}. Adjust RECEIPT_DOCKERFILE or RECEIPT_BUILD_CONTEXT." >&2
  exit 1
fi

DOCKERFILE_RELATIVE=$(DOCKERFILE_ABS="${DOCKERFILE_ABS}" BUILD_CONTEXT_ABS="${BUILD_CONTEXT_ABS}" python3 - <<'PY'
import os
from pathlib import Path

dockerfile = Path(os.environ["DOCKERFILE_ABS"])
context = Path(os.environ["BUILD_CONTEXT_ABS"])
try:
    print(dockerfile.relative_to(context))
except ValueError as exc:
    raise SystemExit(f"Dockerfile {dockerfile} is not within build context {context}: {exc}")
PY
)

if [[ -z "${DOCKERFILE_RELATIVE}" ]]; then
  echo "Failed to derive Dockerfile path relative to build context. Ensure ${DOCKERFILE_PATH} is within ${BUILD_CONTEXT}." >&2
  exit 1
fi

VERTEX_AI_PROJECT_ID="${VERTEX_AI_PROJECT_ID:-${PROJECT_ID}}"
VERTEX_AI_LOCATION="${VERTEX_AI_LOCATION:-${REGION}}"
VERTEX_AI_GEMINI_MODEL="${VERTEX_AI_GEMINI_MODEL:-gemini-2.0-flash}"
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
append_env_var "PROJECT_ID" "$PROJECT_ID"
append_env_var "RECEIPT_FIRESTORE_COLLECTION" "$RECEIPT_FIRESTORE_COLLECTION"
append_env_var "LOGGING_PROJECT_ID" "${LOGGING_PROJECT_ID:-${PROJECT_ID}}"
append_env_var "AI_STUDIO_API_KEY" "${AI_STUDIO_API_KEY:-}"

cleanup_bucket_notifications() {
  local notifications notification_id
  notifications="$(gcloud storage buckets notifications list "gs://${GCS_BUCKET}" --format="value(id)" 2>/dev/null || true)"

  if [[ -z "${notifications}" ]]; then
    echo "No Cloud Storage notifications to remove for gs://${GCS_BUCKET}."
    return
  fi

  echo "Removing legacy Cloud Storage notifications from gs://${GCS_BUCKET} to prevent orphaned push attempts."
  while IFS= read -r notification_id; do
    if [[ -z "${notification_id}" ]]; then
      continue
    fi
    if gcloud storage buckets notifications delete "${notification_id}" --bucket="${GCS_BUCKET}" --quiet >/dev/null 2>&1; then
      echo "Deleted notification ${notification_id} from gs://${GCS_BUCKET}."
      continue
    fi
    gcloud storage buckets notifications delete "${notification_id}" --bucket="gs://${GCS_BUCKET}" --quiet >/dev/null 2>&1 || \
      echo "Warning: Failed to delete notification ${notification_id}; remove it manually if it still exists."
  done <<< "${notifications}"
}

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
  echo "Using Cloud Run region ${REGION}, bucket is in ${BUCKET_REGION_LOWER}. Consider aligning regions for lower latency." >&2
fi

set -x

gcloud config set project "$PROJECT_ID"

gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  firestore.googleapis.com \
  storage.googleapis.com \
  aiplatform.googleapis.com \
  --quiet

if ! gcloud artifacts repositories describe "$ARTIFACT_REPO" --location="$REGION" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$ARTIFACT_REPO" \
    --repository-format=docker \
    --location="$REGION" \
    --description="Container images for receipt processor"
fi

if ! gcloud firestore databases describe --database="(default)" --project="$PROJECT_ID" --format="value(name)" >/dev/null 2>&1; then
  gcloud firestore databases create \
    --location="$REGION" \
    --type=firestore-native \
    --project="$PROJECT_ID"
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

gcloud storage buckets add-iam-policy-binding "gs://${GCS_BUCKET}" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/storage.objectAdmin" \
  --quiet || true

IMAGE_RESOURCE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPO}/${SERVICE_NAME}"
IMAGE_URI="${IMAGE_RESOURCE}:$(date +%Y%m%d-%H%M%S)"

gcloud builds submit "$BUILD_CONTEXT" \
  --config "$CLOUD_BUILD_CONFIG" \
  --substitutions "_IMAGE_URI=${IMAGE_URI},_DOCKERFILE=${DOCKERFILE_RELATIVE}"

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

if [[ -n "$WEB_SERVICE_ACCOUNT" ]]; then
  gcloud run services add-iam-policy-binding "$SERVICE_NAME" \
    --region "$REGION" \
    --member "serviceAccount:${WEB_SERVICE_ACCOUNT}" \
    --role "roles/run.invoker" \
    --quiet || true
fi

if [[ -n "$ADDITIONAL_INVOKER_SERVICE_ACCOUNTS" ]]; then
  IFS=',' read -ra EXTRA_INVOKERS <<< "$ADDITIONAL_INVOKER_SERVICE_ACCOUNTS"
  for invoker in "${EXTRA_INVOKERS[@]}"; do
    trimmed="$(echo "$invoker" | xargs)"
    if [[ -z "$trimmed" ]]; then
      continue
    fi
    gcloud run services add-iam-policy-binding "$SERVICE_NAME" \
      --region "$REGION" \
      --member "serviceAccount:${trimmed}" \
      --role "roles/run.invoker" \
      --quiet || true
  done
fi

cleanup_bucket_notifications

SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" --region "$REGION" --format "value(status.url)")

echo "Receipt processor deployed: ${SERVICE_URL}"

gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=${SERVICE_NAME}" \
  --limit 20 \
  --format="table(timestamp, textPayload)"

set +x

echo "✅ Receipt processor deployment complete."
