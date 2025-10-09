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

FIRESTORE_USERS_COLLECTION="${FIRESTORE_USERS_COLLECTION:-users}"
# Seed an initial OAuth administrator so the dashboard can manage roles immediately.
INITIAL_OAUTH_ADMIN_EMAIL="${INITIAL_OAUTH_ADMIN_EMAIL:-pekelund.dev@gmail.com}"
INITIAL_OAUTH_ADMIN_DISPLAY_NAME="${INITIAL_OAUTH_ADMIN_DISPLAY_NAME:-Pekelund.dev}"

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

ACTIVE_PROFILES="${SPRING_PROFILES_ACTIVE:-prod}"
if [[ ",${ACTIVE_PROFILES}," != *",oauth,"* ]]; then
  ACTIVE_PROFILES="${ACTIVE_PROFILES},oauth"
fi

append_env_var "SPRING_PROFILES_ACTIVE" "$ACTIVE_PROFILES"
append_env_var "FIRESTORE_ENABLED" "${FIRESTORE_ENABLED:-true}"
if [[ -n "${SHARED_FIRESTORE_PROJECT_ID}" ]]; then
  append_env_var "FIRESTORE_PROJECT_ID" "${SHARED_FIRESTORE_PROJECT_ID}"
fi
append_env_var "FIRESTORE_USERS_COLLECTION" "${FIRESTORE_USERS_COLLECTION:-}"
append_env_var "GOOGLE_CLIENT_ID" "${GOOGLE_CLIENT_ID:-}"
append_env_var "GOOGLE_CLIENT_SECRET" "${GOOGLE_CLIENT_SECRET:-}"
append_env_var "GCS_ENABLED" "${GCS_ENABLED:-true}"
append_env_var "GCS_PROJECT_ID" "${SHARED_GCS_PROJECT_ID:-}"
append_env_var "GCS_BUCKET" "${GCS_BUCKET:-}"
append_env_var "GCS_CREDENTIALS" "${GCS_CREDENTIALS:-}"

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

if [[ -n "${INITIAL_OAUTH_ADMIN_EMAIL}" ]] && [[ -n "${SHARED_FIRESTORE_PROJECT_ID}" ]]; then
  mapfile -t _admin_identity < <(python3 - <<'PY'
import re
import sys

raw_email = sys.argv[1]
raw_display = sys.argv[2]

email = (raw_email or "").strip().lower()
display = (raw_display or "").strip()

doc_id = re.sub(r'[^a-z0-9-]', '-', email)
doc_id = re.sub(r'-+', '-', doc_id).strip('-')
if not doc_id:
    doc_id = 'bootstrap-admin'

print(email)
print(doc_id)
print(display)
PY
    "${INITIAL_OAUTH_ADMIN_EMAIL}"
    "${INITIAL_OAUTH_ADMIN_DISPLAY_NAME}"
  )

  INITIAL_OAUTH_ADMIN_EMAIL="${_admin_identity[0]}"
  INITIAL_OAUTH_ADMIN_DOCUMENT_ID="${_admin_identity[1]}"
  INITIAL_OAUTH_ADMIN_DISPLAY_NAME="${_admin_identity[2]}"

  if [[ -n "${INITIAL_OAUTH_ADMIN_EMAIL}" ]]; then
    echo "Ensuring OAuth administrator ${INITIAL_OAUTH_ADMIN_EMAIL} exists in ${SHARED_FIRESTORE_PROJECT_ID}/${FIRESTORE_USERS_COLLECTION}" >&2

    ADMIN_EXISTING_JSON="$(mktemp)"
    ADMIN_MUTATED_JSON="$(mktemp)"

    DOCUMENT_PATH="${FIRESTORE_USERS_COLLECTION}/${INITIAL_OAUTH_ADMIN_DOCUMENT_ID}"

    generate_admin_seed_document() {
      local existing_path="$1"
      local output_path="$2"
      local email="$3"
      local display="$4"

      python3 - <<'PY' \
        "${existing_path}" \
        "${output_path}" \
        "${email}" \
        "${display}"
import json
import os
import sys

existing_path, output_path, email, display = sys.argv[1:5]

existing = {}
if existing_path and os.path.exists(existing_path) and os.path.getsize(existing_path) > 0:
    with open(existing_path, 'r', encoding='utf-8') as handle:
        existing = json.load(handle)

existing_fields = existing.get('fields', {}) if isinstance(existing, dict) else {}

def string_field(value):
    return {'stringValue': value}

roles = []
roles_field = existing_fields.get('roles')
if isinstance(roles_field, dict):
    array_value = roles_field.get('arrayValue', {})
    if isinstance(array_value, dict):
        for entry in array_value.get('values', []) or []:
            if isinstance(entry, dict):
                role_value = entry.get('stringValue')
                if role_value and role_value not in roles:
                    roles.append(role_value)

for required_role in ("ROLE_USER", "ROLE_ADMIN"):
    if required_role not in roles:
        roles.append(required_role)

auth_provider = None
auth_field = existing_fields.get('authProvider')
if isinstance(auth_field, dict):
    auth_provider = auth_field.get('stringValue')
if not auth_provider:
    auth_provider = 'oauth'

display = display.strip()

fields = {
    'email': string_field(email),
    'roles': {
        'arrayValue': {
            'values': [string_field(role) for role in roles]
        }
    },
    'authProvider': string_field(auth_provider),
}

if display:
    fields['fullName'] = string_field(display)

with open(output_path, 'w', encoding='utf-8') as handle:
    json.dump({'fields': fields}, handle)
PY
    }

    if gcloud firestore documents describe "${DOCUMENT_PATH}" \
      --project="${SHARED_FIRESTORE_PROJECT_ID}" \
      --format=json >"${ADMIN_EXISTING_JSON}" 2>/dev/null; then
      generate_admin_seed_document "${ADMIN_EXISTING_JSON}" "${ADMIN_MUTATED_JSON}" "${INITIAL_OAUTH_ADMIN_EMAIL}" "${INITIAL_OAUTH_ADMIN_DISPLAY_NAME}"

      ADMIN_UPDATE_MASK="email,roles,authProvider"
      if [[ -n "${INITIAL_OAUTH_ADMIN_DISPLAY_NAME}" ]]; then
        ADMIN_UPDATE_MASK+=",fullName"
      fi

      gcloud firestore documents update "${DOCUMENT_PATH}" \
        --project="${SHARED_FIRESTORE_PROJECT_ID}" \
        --document="${ADMIN_MUTATED_JSON}" \
        --update-mask="${ADMIN_UPDATE_MASK}"
    else
      : >"${ADMIN_EXISTING_JSON}"
      generate_admin_seed_document "${ADMIN_EXISTING_JSON}" "${ADMIN_MUTATED_JSON}" "${INITIAL_OAUTH_ADMIN_EMAIL}" "${INITIAL_OAUTH_ADMIN_DISPLAY_NAME}"

      gcloud firestore documents create "${DOCUMENT_PATH}" \
        --project="${SHARED_FIRESTORE_PROJECT_ID}" \
        --document="${ADMIN_MUTATED_JSON}"
    fi

    rm -f "${ADMIN_EXISTING_JSON}" "${ADMIN_MUTATED_JSON}"
    unset -f generate_admin_seed_document
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

gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=${SERVICE_NAME}" \
  --limit 20 \
  --format="table(timestamp, textPayload)"
