#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
INFRA_DIR="${REPO_ROOT}/infra/terraform/infrastructure"
DEPLOY_DIR="${REPO_ROOT}/infra/terraform/deployment"

PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
REGION=${REGION:-us-east1}
WEB_SERVICE_NAME=${WEB_SERVICE_NAME:-pklnd-web}
RECEIPT_SERVICE_NAME=${RECEIPT_SERVICE_NAME:-pklnd-receipts}
WEB_DOCKERFILE=${WEB_DOCKERFILE:-Dockerfile}
WEB_BUILD_CONTEXT=${WEB_BUILD_CONTEXT:-${REPO_ROOT}}
RECEIPT_DOCKERFILE=${RECEIPT_DOCKERFILE:-receipt-parser/Dockerfile}
CLOUD_BUILD_CONFIG=${CLOUD_BUILD_CONFIG:-receipt-parser/cloudbuild.yaml}
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
  eval "$(python - <<'PY'
import json, os, sys

outputs = json.load(sys.stdin)


def get_output(key, default=""):
    return outputs.get(key, {}).get("value", default)


values = {
    "bucket_name_from_tf": get_output("bucket_name"),
    "web_repo_from_tf": os.path.basename(get_output("web_artifact_registry")) or "web",
    "receipt_repo_from_tf": os.path.basename(get_output("receipt_artifact_registry")) or "receipts",
    "web_sa_from_tf": get_output("web_service_account_email"),
    "receipt_sa_from_tf": get_output("receipt_service_account_email"),
}

print("\n".join(f"{k}='{v}'" for k, v in values.items()))
PY
 <<<"${infra_outputs}")"

  bucket_name=${bucket_name:-${bucket_name_from_tf}}
  web_repo=${web_repo_from_tf:-${web_repo}}
  receipt_repo=${receipt_repo_from_tf:-${receipt_repo}}
  web_sa=${web_sa_from_tf:-${web_sa}}
  receipt_sa=${receipt_sa_from_tf:-${receipt_sa}}
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

tfvars_file=$(mktemp)
chmod 600 "${tfvars_file}"
trap 'rm -f "${tfvars_file}"' EXIT

ALLOW_UNAUTHENTICATED_WEB=${ALLOW_UNAUTHENTICATED_WEB:-true}
CUSTOM_DOMAIN=${CUSTOM_DOMAIN:-}

TFVARS_FILE="${tfvars_file}" \
PROJECT_ID="${PROJECT_ID}" \
REGION="${REGION}" \
BUCKET_NAME_VAL="${bucket_name}" \
WEB_SA_VAL="${web_sa}" \
RECEIPT_SA_VAL="${receipt_sa}" \
APP_SECRET_NAME_VAL="${APP_SECRET_NAME}" \
GOOGLE_CLIENT_ID_VAL="${GOOGLE_CLIENT_ID}" \
GOOGLE_CLIENT_SECRET_VAL="${GOOGLE_CLIENT_SECRET}" \
AI_STUDIO_API_KEY_VAL="${AI_STUDIO_API_KEY}" \
WEB_IMAGE_VAL="${web_image}" \
RECEIPT_IMAGE_VAL="${receipt_image}" \
WEB_SERVICE_NAME_VAL="${WEB_SERVICE_NAME}" \
RECEIPT_SERVICE_NAME_VAL="${RECEIPT_SERVICE_NAME}" \
FIRESTORE_PROJECT_ID_VAL="${FIRESTORE_PROJECT_ID:-${PROJECT_ID}}" \
GCS_PROJECT_ID_VAL="${GCS_PROJECT_ID:-${PROJECT_ID}}" \
VERTEX_AI_PROJECT_ID_VAL="${VERTEX_AI_PROJECT_ID:-${PROJECT_ID}}" \
VERTEX_AI_LOCATION_VAL="${VERTEX_AI_LOCATION:-${REGION}}" \
LOGGING_PROJECT_ID_VAL="${LOGGING_PROJECT_ID:-${PROJECT_ID}}" \
ALLOW_UNAUTH_WEB="${ALLOW_UNAUTHENTICATED_WEB}" \
CUSTOM_DOMAIN_VAL="${CUSTOM_DOMAIN}" \
python - <<'PY'
import json
import os
import pathlib

tfvars_path = pathlib.Path(os.environ["TFVARS_FILE"])

def fmt(value, key):
    if key == "allow_unauthenticated_web":
        return str(value).lower()
    return json.dumps(value)


tfvars = {
    "project_id": os.environ["PROJECT_ID"],
    "region": os.environ["REGION"],
    "bucket_name": os.environ["BUCKET_NAME_VAL"],
    "web_service_account_email": os.environ["WEB_SA_VAL"],
    "receipt_service_account_email": os.environ["RECEIPT_SA_VAL"],
    "secret_name": os.environ["APP_SECRET_NAME_VAL"],
    "google_client_id": os.environ["GOOGLE_CLIENT_ID_VAL"],
    "google_client_secret": os.environ["GOOGLE_CLIENT_SECRET_VAL"],
    "ai_studio_api_key": os.environ["AI_STUDIO_API_KEY_VAL"],
    "web_image": os.environ["WEB_IMAGE_VAL"],
    "receipt_image": os.environ["RECEIPT_IMAGE_VAL"],
    "web_service_name": os.environ["WEB_SERVICE_NAME_VAL"],
    "receipt_service_name": os.environ["RECEIPT_SERVICE_NAME_VAL"],
    "firestore_project_id": os.environ["FIRESTORE_PROJECT_ID_VAL"],
    "gcs_project_id": os.environ["GCS_PROJECT_ID_VAL"],
    "vertex_ai_project_id": os.environ["VERTEX_AI_PROJECT_ID_VAL"],
    "vertex_ai_location": os.environ["VERTEX_AI_LOCATION_VAL"],
    "logging_project_id": os.environ["LOGGING_PROJECT_ID_VAL"],
    "allow_unauthenticated_web": os.environ["ALLOW_UNAUTH_WEB"],
    "custom_domain": os.environ["CUSTOM_DOMAIN_VAL"],
}

tfvars_path.write_text("\n".join(f"{key} = {fmt(value, key)}" for key, value in tfvars.items()) + "\n")
PY

echo "Building web image ${web_image}"
gcloud builds submit "${WEB_BUILD_CONTEXT}" \
  --tag "${web_image}" \
  --project "${PROJECT_ID}" \
  --timeout=1800s

echo "Building receipt processor image ${receipt_image}"
gcloud builds submit "${REPO_ROOT}" \
  --config "${CLOUD_BUILD_CONFIG}" \
  --substitutions "_IMAGE_URI=${receipt_image},_DOCKERFILE=${RECEIPT_DOCKERFILE}" \
  --project "${PROJECT_ID}" \
  --timeout=1800s

terraform -chdir="${DEPLOY_DIR}" init -input=false
terraform -chdir="${DEPLOY_DIR}" apply -input=false -auto-approve -var-file="${tfvars_file}" "$@"
