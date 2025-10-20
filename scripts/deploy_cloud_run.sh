#!/usr/bin/env bash
set -euo pipefail

# This script provisions and deploys the web application to Cloud Run.
# Configure the environment variables below before running the script.

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-europe-north1}"
SERVICE_NAME="${SERVICE_NAME:-pklnd-web}"
SA_NAME="${SA_NAME:-cloud-run-runtime}"
ARTIFACT_REPO="${ARTIFACT_REPO:-web}"
# Pub/Sub topic and subscription used for receipt processing requests.
RECEIPT_PUBSUB_TOPIC="${RECEIPT_PUBSUB_TOPIC:-receipt-processing}"
RECEIPT_PUBSUB_PROJECT_ID="${RECEIPT_PUBSUB_PROJECT_ID:-${PROJECT_ID}}"
RECEIPT_PUBSUB_SUBSCRIPTION="${RECEIPT_PUBSUB_SUBSCRIPTION:-receipt-processing-web}"
# Firestore is shared between the Cloud Run app, the Cloud Function and the user registration flow.
# Default the shared project to the deployment project, but allow overrides when the
# Firestore database lives in a different project.
SHARED_FIRESTORE_PROJECT_ID="${SHARED_FIRESTORE_PROJECT_ID:-${PROJECT_ID}}"
SHARED_GCS_PROJECT_ID="${SHARED_GCS_PROJECT_ID:-${PROJECT_ID}}"
# Allow operators to point to a downloaded OAuth client JSON file instead of copying
# the ID/secret manually.
if [[ -n "${GOOGLE_OAUTH_CREDENTIALS_FILE:-}" ]] && \
   ([[ -z "${GOOGLE_CLIENT_ID:-}" ]] || [[ -z "${GOOGLE_CLIENT_SECRET:-}" ]]); then
  if [[ ! -f "${GOOGLE_OAUTH_CREDENTIALS_FILE}" ]]; then
    echo "GOOGLE_OAUTH_CREDENTIALS_FILE=${GOOGLE_OAUTH_CREDENTIALS_FILE} does not exist" >&2
    exit 1
  fi

  mapfile -t _oauth_from_file < <(python3 - "${GOOGLE_OAUTH_CREDENTIALS_FILE}" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, 'r', encoding='utf-8') as handle:
    data = json.load(handle)

candidate = None
for key in ("web", "installed", "oauth_client"):
    if isinstance(data, dict) and key in data:
        candidate = data[key]
        break

if candidate is None:
    candidate = data

client_id = candidate.get("client_id")
client_secret = candidate.get("client_secret")

if not client_id or not client_secret:
    raise SystemExit("Missing client_id/client_secret in OAuth credentials JSON")

print(client_id)
print(client_secret)
PY
  ) || {
    echo "Failed to parse Google OAuth credentials JSON at ${GOOGLE_OAUTH_CREDENTIALS_FILE}" >&2
    exit 1
  }

  if [[ -z "${GOOGLE_CLIENT_ID:-}" ]]; then
    GOOGLE_CLIENT_ID="${_oauth_from_file[0]}"
  fi
  if [[ -z "${GOOGLE_CLIENT_SECRET:-}" ]]; then
    GOOGLE_CLIENT_SECRET="${_oauth_from_file[1]}"
  fi
fi

# Append shared configuration to the Cloud Run environment variables so every component
# talks to the same Firestore project and OAuth client.
ENV_VARS_LIST=()

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

if [[ -z "${GOOGLE_CLIENT_ID:-}" ]] || [[ -z "${GOOGLE_CLIENT_SECRET:-}" ]]; then
  printf '%s\n' \
    'GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET must be provided (directly or via GOOGLE_OAUTH_CREDENTIALS_FILE) when deploying with this script.' >&2
  exit 1
fi

if [[ -z "${GCS_BUCKET:-}" ]]; then
  printf '%s\n' \
    'GCS_BUCKET must be set when deploying with this script so receipt uploads can target the correct bucket.' >&2
  exit 1
fi

if [[ -z "${RECEIPT_PROCESSING_PUBSUB_VERIFICATION_TOKEN:-}" ]]; then
  RECEIPT_PROCESSING_PUBSUB_VERIFICATION_TOKEN="$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)"
  echo "🔑 Generated Pub/Sub verification token for receipt processing endpoint"
fi

ACTIVE_PROFILES="${SPRING_PROFILES_ACTIVE:-prod}"
if [[ ",${ACTIVE_PROFILES}," != *",oauth,"* ]]; then
  ACTIVE_PROFILES="${ACTIVE_PROFILES},oauth"
fi

append_env_var "SPRING_PROFILES_ACTIVE" "$ACTIVE_PROFILES"
append_env_var "FIRESTORE_ENABLED" "${FIRESTORE_ENABLED:-true}"
if [[ -n "${SHARED_FIRESTORE_PROJECT_ID}" ]]; then
  append_env_var "FIRESTORE_PROJECT_ID" "${SHARED_FIRESTORE_PROJECT_ID}"
fi
append_env_var "GOOGLE_CLIENT_ID" "${GOOGLE_CLIENT_ID:-}"
append_env_var "GOOGLE_CLIENT_SECRET" "${GOOGLE_CLIENT_SECRET:-}"
append_env_var "GCS_ENABLED" "${GCS_ENABLED:-true}"
append_env_var "GCS_PROJECT_ID" "${SHARED_GCS_PROJECT_ID:-}"
append_env_var "GCS_BUCKET" "${GCS_BUCKET:-}"
append_env_var "GCS_CREDENTIALS" "${GCS_CREDENTIALS:-}"
append_env_var "RECEIPT_PROCESSING_PUBSUB_VERIFICATION_TOKEN" "${RECEIPT_PROCESSING_PUBSUB_VERIFICATION_TOKEN:-}"

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

  echo "Could not find a safe delimiter for Cloud Run environment variables" >&2
  exit 1
}

ENV_VARS_ARG=""
if (( ${#ENV_VARS_LIST[@]} > 0 )); then
  DELIM="$(choose_env_delimiter)"
  ENV_VARS_ARG="^${DELIM}^$(IFS="$DELIM"; printf '%s' "${ENV_VARS_LIST[*]}")"
fi

if [[ -z "$ENV_VARS_ARG" ]]; then
  echo "Failed to assemble Cloud Run environment variables" >&2
  exit 1
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
  storage.googleapis.com \
  pubsub.googleapis.com \
  secretmanager.googleapis.com \
  --quiet

# Create Artifact Registry repository if it does not already exist
if ! gcloud artifacts repositories describe "$ARTIFACT_REPO" \
  --location="$REGION" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$ARTIFACT_REPO" \
    --repository-format=docker \
    --location="$REGION" \
    --description="Container images for Cloud Run"
else
  echo "Artifact Registry repository ${ARTIFACT_REPO} already exists; skipping creation."
fi

# Create Firestore database in the shared project if missing
if [[ -n "${SHARED_FIRESTORE_PROJECT_ID}" ]]; then
  if ! gcloud firestore databases describe --database="(default)" \
    --project="${SHARED_FIRESTORE_PROJECT_ID}" \
    --format="value(name)" >/dev/null 2>&1; then
    gcloud firestore databases create \
      --location="$REGION" \
      --type=firestore-native \
      --project="${SHARED_FIRESTORE_PROJECT_ID}"
  else
    echo "Firestore database already exists in project ${SHARED_FIRESTORE_PROJECT_ID}; skipping creation."
  fi
fi

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

if [[ -n "${SHARED_GCS_PROJECT_ID}" ]]; then
  gcloud projects add-iam-policy-binding "${SHARED_GCS_PROJECT_ID}" \
    --member "serviceAccount:${SA_EMAIL}" \
    --role "roles/storage.objectAdmin" --condition=None || true
fi

# Add Secret Manager role only if secrets are required; safe to attempt.
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/secretmanager.secretAccessor" --condition=None || true

PUBSUB_TOPIC_PATH="projects/${RECEIPT_PUBSUB_PROJECT_ID}/topics/${RECEIPT_PUBSUB_TOPIC}"
echo "📬 Ensuring Pub/Sub topic $PUBSUB_TOPIC_PATH exists..."
if ! gcloud pubsub topics describe "$PUBSUB_TOPIC_PATH" --format="value(name)" >/dev/null 2>&1; then
  gcloud pubsub topics create "$PUBSUB_TOPIC_PATH"
else
  echo "Pub/Sub topic already present"
fi

# Build and push the image
IMAGE_URI="${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPO}/${SERVICE_NAME}:$(date +%Y%m%d-%H%M%S)"
DOCKERFILE_PATH="${DOCKERFILE_PATH:-Dockerfile}"

if [[ ! -f "$DOCKERFILE_PATH" ]]; then
  echo "Dockerfile path ${DOCKERFILE_PATH} does not exist" >&2
  exit 1
fi

echo "🏗️  Building container image with ${DOCKERFILE_PATH} (GraalVM native build + distroless base runtime)."

gcloud builds submit \
  --tag "$IMAGE_URI" \
  --file "$DOCKERFILE_PATH"

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
  --set-env-vars "${ENV_VARS_ARG}" \
  --min-instances 0 \
  --max-instances 10

# Configure custom domain mapping
if [[ -n "$DOMAIN" ]]; then
  if gcloud beta run domain-mappings describe \
    --domain "$DOMAIN" \
    --region "$REGION" >/dev/null 2>&1; then
    echo "Domain mapping for ${DOMAIN} already exists; leaving it unchanged."
  else
    if ! create_output=$(gcloud beta run domain-mappings create \
      --service "$SERVICE_NAME" \
      --domain "$DOMAIN" \
      --region "$REGION" 2>&1); then
      if [[ "$create_output" == *"already exists"* ]]; then
        echo "Domain mapping for ${DOMAIN} already exists; skipping creation."
      else
        printf '%s\n' "$create_output" >&2
        exit 1
      fi
    else
      printf '%s\n' "$create_output"
    fi
  fi

  gcloud beta run domain-mappings describe \
    --domain "$DOMAIN" \
    --region "$REGION"
else
  echo "DOMAIN not set; skipping custom domain mapping."
fi

# Display service URL and tail recent logs
SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" \
  --region "$REGION" \
  --format "value(status.url)")

echo "Service deployed: ${SERVICE_URL}"

PUSH_ENDPOINT="${SERVICE_URL}/internal/pubsub/receipt-processing"
SUBSCRIPTION_PATH="projects/${RECEIPT_PUBSUB_PROJECT_ID}/subscriptions/${RECEIPT_PUBSUB_SUBSCRIPTION}"
echo "📨 Configuring Pub/Sub push subscription ${SUBSCRIPTION_PATH} -> ${PUSH_ENDPOINT}"
if gcloud pubsub subscriptions describe "$SUBSCRIPTION_PATH" --format="value(name)" >/dev/null 2>&1; then
  gcloud pubsub subscriptions update "$SUBSCRIPTION_PATH" \
    --push-endpoint="$PUSH_ENDPOINT" \
    --push-token="$RECEIPT_PROCESSING_PUBSUB_VERIFICATION_TOKEN"
else
  gcloud pubsub subscriptions create "$SUBSCRIPTION_PATH" \
    --topic="$PUBSUB_TOPIC_PATH" \
    --push-endpoint="$PUSH_ENDPOINT" \
    --push-token="$RECEIPT_PROCESSING_PUBSUB_VERIFICATION_TOKEN"
fi

echo "Pub/Sub verification token: ${RECEIPT_PROCESSING_PUBSUB_VERIFICATION_TOKEN}"

gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=${SERVICE_NAME}" \
  --limit 20 \
  --format="table(timestamp, textPayload)"
