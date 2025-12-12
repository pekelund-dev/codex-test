#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TF_DIR="${REPO_ROOT}/infra/terraform/infrastructure"

PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
REGION=${REGION:-us-east1}
FIRESTORE_LOCATION=${FIRESTORE_LOCATION:-${REGION}}
FIRESTORE_DATABASE_NAME=${FIRESTORE_DATABASE_NAME:-receipts-db}
BUCKET_NAME=${BUCKET_NAME:-}
APP_SECRET_NAME=${APP_SECRET_NAME:-pklnd-app-config}

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured in gcloud before destroying infrastructure." >&2
  exit 1
fi

if [[ -z "${BUCKET_NAME}" ]]; then
  BUCKET_NAME="pklnd-receipts-${PROJECT_ID}"
fi

export TF_VAR_project_id="${PROJECT_ID}"
export TF_VAR_region="${REGION}"
export TF_VAR_firestore_location="${FIRESTORE_LOCATION}"
export TF_VAR_firestore_database_name="${FIRESTORE_DATABASE_NAME}"
export TF_VAR_bucket_name="${BUCKET_NAME}"
export TF_VAR_app_secret_name="${APP_SECRET_NAME}"

# Configure Terraform state bucket
STATE_BUCKET="pklnd-terraform-state-${PROJECT_ID}"

# Initialize with backend if state bucket exists
if gsutil ls "gs://${STATE_BUCKET}" >/dev/null 2>&1; then
  echo "Using existing Terraform state bucket: ${STATE_BUCKET}"
  terraform -chdir="${TF_DIR}" init -input=false \
    -backend-config="bucket=${STATE_BUCKET}" \
    -backend-config="prefix=infrastructure"
else
  echo "Warning: State bucket ${STATE_BUCKET} not found. Initializing without backend."
  terraform -chdir="${TF_DIR}" init -input=false
fi

terraform -chdir="${TF_DIR}" destroy -input=false -auto-approve "$@"
