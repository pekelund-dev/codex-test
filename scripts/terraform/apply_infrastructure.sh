#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TF_DIR="${REPO_ROOT}/infra/terraform/infrastructure"

PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
REGION=${REGION:-europe-north1}
FIRESTORE_LOCATION=${FIRESTORE_LOCATION:-${REGION}}
BUCKET_NAME=${BUCKET_NAME:-}
APP_SECRET_FILE=${APP_SECRET_FILE:-}
APP_SECRET_NAME=${APP_SECRET_NAME:-pklnd-app-config}

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured in gcloud before applying infrastructure." >&2
  exit 1
fi

if [[ -z "${BUCKET_NAME}" ]]; then
  BUCKET_NAME="pklnd-receipts-${PROJECT_ID}"
fi

if [[ -n "${APP_SECRET_FILE}" ]]; then
  if [[ ! -f "${APP_SECRET_FILE}" ]]; then
    echo "APP_SECRET_FILE set to ${APP_SECRET_FILE}, but the file does not exist." >&2
    exit 1
  fi
  export TF_VAR_app_secret_json="$(cat "${APP_SECRET_FILE}")"
fi

export TF_VAR_project_id="${PROJECT_ID}"
export TF_VAR_region="${REGION}"
export TF_VAR_firestore_location="${FIRESTORE_LOCATION}"
export TF_VAR_bucket_name="${BUCKET_NAME}"
export TF_VAR_app_secret_name="${APP_SECRET_NAME}"

terraform -chdir="${TF_DIR}" init -input=false
terraform -chdir="${TF_DIR}" apply -input=false -auto-approve "$@"
