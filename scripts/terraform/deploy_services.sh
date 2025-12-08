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

bucket_name=${BUCKET_NAME:-}
web_repo=web
receipt_repo=receipts
web_sa="cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
receipt_sa="receipt-processor@${PROJECT_ID}.iam.gserviceaccount.com"

tf_output_raw() {
  terraform -chdir="${INFRA_DIR}" output -raw "$1" 2>/dev/null || true
}

bucket_name_from_tf=$(tf_output_raw bucket_name)
web_registry_from_tf=$(tf_output_raw web_artifact_registry)
receipt_registry_from_tf=$(tf_output_raw receipt_artifact_registry)
web_sa_from_tf=$(tf_output_raw web_service_account_email)
receipt_sa_from_tf=$(tf_output_raw receipt_service_account_email)

if [[ -n "${web_registry_from_tf}" ]]; then
  web_repo_from_tf=${web_registry_from_tf##*/}
fi

if [[ -n "${receipt_registry_from_tf}" ]]; then
  receipt_repo_from_tf=${receipt_registry_from_tf##*/}
fi

bucket_name=${bucket_name:-${bucket_name_from_tf}}
web_repo=${web_repo_from_tf:-${web_repo}}
receipt_repo=${receipt_repo_from_tf:-${receipt_repo}}
web_sa=${web_sa_from_tf:-${web_sa}}
receipt_sa=${receipt_sa_from_tf:-${receipt_sa}}

bucket_name=${bucket_name:-"pklnd-receipts-${PROJECT_ID}"}

timestamp=$(date +%Y%m%d-%H%M%S)
web_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/${web_repo}/${WEB_SERVICE_NAME}:${timestamp}"
receipt_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/${receipt_repo}/${RECEIPT_SERVICE_NAME}:${timestamp}"

if ! gcloud secrets describe "${APP_SECRET_NAME}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  echo "Secret ${APP_SECRET_NAME} not found in project ${PROJECT_ID}. Ensure infrastructure is applied first." >&2
  exit 1
fi

secret_json=$(gcloud secrets versions access "${APP_SECRET_VERSION}" --secret "${APP_SECRET_NAME}" --project "${PROJECT_ID}")

parse_json_field() {
  local json_payload="$1"
  local key="$2"

  if command -v jq >/dev/null 2>&1; then
    echo "${json_payload}" | jq -r --arg key "${key}" '.[$key] // empty'
  else
    echo "${json_payload}" | sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"
  fi
}

GOOGLE_CLIENT_ID=$(parse_json_field "${secret_json}" "google_client_id")
GOOGLE_CLIENT_SECRET=$(parse_json_field "${secret_json}" "google_client_secret")
AI_STUDIO_API_KEY=$(parse_json_field "${secret_json}" "ai_studio_api_key")

if [[ -z "${GOOGLE_CLIENT_ID}" || -z "${GOOGLE_CLIENT_SECRET}" ]]; then
  echo "Unified secret must include google_client_id and google_client_secret" >&2
  exit 1
fi

tfvars_file=$(mktemp)
chmod 600 "${tfvars_file}"
trap 'rm -f "${tfvars_file}"' EXIT

ALLOW_UNAUTHENTICATED_WEB=${ALLOW_UNAUTHENTICATED_WEB:-true}
CUSTOM_DOMAIN=${CUSTOM_DOMAIN:-}

json_escape() {
  local raw_value="$1"

  if command -v jq >/dev/null 2>&1; then
    jq -Rs '.' <<<"${raw_value}"
  else
    printf '"%s"' "$(printf '%s' "${raw_value}" | sed 's/\\/\\\\/g; s/"/\\"/g')"
  fi
}

allow_unauth_value=$(printf '%s' "${ALLOW_UNAUTHENTICATED_WEB}" | tr '[:upper:]' '[:lower:]')

cat >"${tfvars_file}" <<EOF
project_id = $(json_escape "${PROJECT_ID}")
region = $(json_escape "${REGION}")
bucket_name = $(json_escape "${bucket_name}")
web_service_account_email = $(json_escape "${web_sa}")
receipt_service_account_email = $(json_escape "${receipt_sa}")
secret_name = $(json_escape "${APP_SECRET_NAME}")
google_client_id = $(json_escape "${GOOGLE_CLIENT_ID}")
google_client_secret = $(json_escape "${GOOGLE_CLIENT_SECRET}")
ai_studio_api_key = $(json_escape "${AI_STUDIO_API_KEY}")
web_image = $(json_escape "${web_image}")
receipt_image = $(json_escape "${receipt_image}")
web_service_name = $(json_escape "${WEB_SERVICE_NAME}")
receipt_service_name = $(json_escape "${RECEIPT_SERVICE_NAME}")
firestore_project_id = $(json_escape "${FIRESTORE_PROJECT_ID:-${PROJECT_ID}}")
gcs_project_id = $(json_escape "${GCS_PROJECT_ID:-${PROJECT_ID}}")
vertex_ai_project_id = $(json_escape "${VERTEX_AI_PROJECT_ID:-${PROJECT_ID}}")
vertex_ai_location = $(json_escape "${VERTEX_AI_LOCATION:-${REGION}}")
logging_project_id = $(json_escape "${LOGGING_PROJECT_ID:-${PROJECT_ID}}")
allow_unauthenticated_web = ${allow_unauth_value}
custom_domain = $(json_escape "${CUSTOM_DOMAIN}")
EOF

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
