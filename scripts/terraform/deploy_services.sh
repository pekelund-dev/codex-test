#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
INFRA_DIR="${REPO_ROOT}/infra/terraform/infrastructure"
DEPLOY_DIR="${REPO_ROOT}/infra/terraform/deployment"

PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
REGION=${REGION:-europe-north1}
WEB_SERVICE_NAME=${WEB_SERVICE_NAME:-pklnd-web}
RECEIPT_SERVICE_NAME=${RECEIPT_SERVICE_NAME:-pklnd-receipts}
WEB_DOCKERFILE=${WEB_DOCKERFILE:-Dockerfile}
WEB_BUILD_CONTEXT=${WEB_BUILD_CONTEXT:-${REPO_ROOT}}
RECEIPT_DOCKERFILE=${RECEIPT_DOCKERFILE:-receipt-parser/Dockerfile}
CLOUD_BUILD_CONFIG=${RECEIPT_CLOUD_BUILD_CONFIG:-receipt-parser/cloudbuild.yaml}
APP_SECRET_NAME=${APP_SECRET_NAME:-pklnd-app-config}
APP_SECRET_VERSION=${APP_SECRET_VERSION:-latest}

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured in gcloud before deploying services." >&2
  exit 1
fi

if [[ ! -d "${WEB_BUILD_CONTEXT}" ]]; then
  echo "WEB_BUILD_CONTEXT ${WEB_BUILD_CONTEXT} was not found." >&2
  exit 1
fi

if [[ ! -f "${WEB_BUILD_CONTEXT%/}/${WEB_DOCKERFILE}" ]]; then
  echo "Dockerfile ${WEB_DOCKERFILE} not found in ${WEB_BUILD_CONTEXT}." >&2
  exit 1
fi

if [[ ! -f "${REPO_ROOT}/${CLOUD_BUILD_CONFIG}" ]]; then
  echo "Cloud Build configuration ${CLOUD_BUILD_CONFIG} was not found under the repository root." >&2
  exit 1
fi

infra_outputs=$(terraform -chdir="${INFRA_DIR}" output -json 2>/dev/null || true)

bucket_name=${BUCKET_NAME:-}
web_repo=web
receipt_repo=receipts
web_sa="cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
receipt_sa="receipt-processor@${PROJECT_ID}.iam.gserviceaccount.com"

if [[ -n "${infra_outputs}" ]]; then
  bucket_name=${bucket_name:-$(python - <<'PY'
import json,sys
outputs=json.load(sys.stdin)
print(outputs.get("bucket_name",{}).get("value",""))
PY
 <<<"${infra_outputs}")}

  web_repo=$(python - <<'PY'
import json,sys,os
outputs=json.load(sys.stdin)
value=outputs.get("web_artifact_registry",{}).get("value","")
print(os.path.basename(value) or "web")
PY
 <<<"${infra_outputs}")

  receipt_repo=$(python - <<'PY'
import json,sys,os
outputs=json.load(sys.stdin)
value=outputs.get("receipt_artifact_registry",{}).get("value","")
print(os.path.basename(value) or "receipts")
PY
 <<<"${infra_outputs}")

  web_sa=$(python - <<'PY'
import json,sys
outputs=json.load(sys.stdin)
print(outputs.get("web_service_account_email",{}).get("value",""))
PY
 <<<"${infra_outputs}") or "${web_sa}"

  receipt_sa=$(python - <<'PY'
import json,sys
outputs=json.load(sys.stdin)
print(outputs.get("receipt_service_account_email",{}).get("value",""))
PY
 <<<"${infra_outputs}") or "${receipt_sa}"
fi

bucket_name=${bucket_name:-"pklnd-receipts-${PROJECT_ID}"}

timestamp=$(date +%Y%m%d-%H%M%S)
web_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/${web_repo}/${WEB_SERVICE_NAME}:${timestamp}"
receipt_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/${receipt_repo}/${RECEIPT_SERVICE_NAME}:${timestamp}"

if ! gcloud secrets describe "${APP_SECRET_NAME}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  echo "Secret ${APP_SECRET_NAME} not found in project ${PROJECT_ID}. Ensure infrastructure is applied first." >&2
  exit 1
fi

secret_json=$(gcloud secrets versions access "${APP_SECRET_VERSION}" --secret "${APP_SECRET_NAME}" --project "${PROJECT_ID}")

read -r GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET AI_STUDIO_API_KEY < <(
  SECRET_JSON="${secret_json}" python - <<'PY'
import json
import os

payload = json.loads(os.environ["SECRET_JSON"])

client_id = payload.get("google_client_id")
client_secret = payload.get("google_client_secret")
ai_key = payload.get("ai_studio_api_key", "")

if not client_id or not client_secret:
    raise SystemExit("Unified secret must include google_client_id and google_client_secret")

print(client_id)
print(client_secret)
print(ai_key)
PY
)

export TF_VAR_project_id="${PROJECT_ID}"
export TF_VAR_region="${REGION}"
export TF_VAR_bucket_name="${bucket_name}"
export TF_VAR_web_service_account_email="${web_sa}"
export TF_VAR_receipt_service_account_email="${receipt_sa}"
export TF_VAR_secret_name="${APP_SECRET_NAME}"
export TF_VAR_google_client_id="${GOOGLE_CLIENT_ID}"
export TF_VAR_google_client_secret="${GOOGLE_CLIENT_SECRET}"
export TF_VAR_ai_studio_api_key="${AI_STUDIO_API_KEY}"
export TF_VAR_web_image="${web_image}"
export TF_VAR_receipt_image="${receipt_image}"
export TF_VAR_web_service_name="${WEB_SERVICE_NAME}"
export TF_VAR_receipt_service_name="${RECEIPT_SERVICE_NAME}"
export TF_VAR_firestore_project_id="${FIRESTORE_PROJECT_ID:-${PROJECT_ID}}"
export TF_VAR_gcs_project_id="${GCS_PROJECT_ID:-${PROJECT_ID}}"
export TF_VAR_vertex_ai_project_id="${VERTEX_AI_PROJECT_ID:-${PROJECT_ID}}"
export TF_VAR_vertex_ai_location="${VERTEX_AI_LOCATION:-${REGION}}"
export TF_VAR_logging_project_id="${LOGGING_PROJECT_ID:-${PROJECT_ID}}"
export TF_VAR_allow_unauthenticated_web="${ALLOW_UNAUTHENTICATED_WEB:-true}"
export TF_VAR_custom_domain="${CUSTOM_DOMAIN:-}"

echo "Building web image ${web_image}"
gcloud builds submit "${WEB_BUILD_CONTEXT}" \
  --tag "${web_image}" \
  --project "${PROJECT_ID}" \
  --timeout="1800"

echo "Building receipt processor image ${receipt_image}"
gcloud builds submit "${REPO_ROOT}" \
  --config "${CLOUD_BUILD_CONFIG}" \
  --substitutions "_IMAGE_URI=${receipt_image},_DOCKERFILE=${RECEIPT_DOCKERFILE}" \
  --project "${PROJECT_ID}" \
  --timeout="1800"

terraform -chdir="${DEPLOY_DIR}" init -input=false
terraform -chdir="${DEPLOY_DIR}" apply -input=false -auto-approve "$@"
