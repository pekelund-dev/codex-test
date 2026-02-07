#!/usr/bin/env bash
set -euo pipefail

# Setup test environment infrastructure
# This script provisions the infrastructure for the test environment:
# - Firestore database (separate from production)
# - Storage bucket for test receipts
# - Secret Manager secret for test credentials
# - Service accounts (can be shared or separate)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TF_DIR="${REPO_ROOT}/infra/terraform/infrastructure"

PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
REGION=${REGION:-us-east1}
FIRESTORE_LOCATION=${FIRESTORE_LOCATION:-${REGION}}
FIRESTORE_DATABASE_NAME=${FIRESTORE_DATABASE_NAME:-receipts-db-test}
BUCKET_NAME=${BUCKET_NAME:-pklnd-receipts-test-${PROJECT_ID}}
APP_SECRET_FILE=${APP_SECRET_FILE:-}
APP_SECRET_NAME=${APP_SECRET_NAME:-pklnd-app-config-test}

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured in gcloud before applying test infrastructure." >&2
  exit 1
fi

echo "=== Test Environment Infrastructure Setup ==="
echo "Project: ${PROJECT_ID}"
echo "Region: ${REGION}"
echo "Firestore DB: ${FIRESTORE_DATABASE_NAME}"
echo "Bucket: ${BUCKET_NAME}"
echo "Secret Name: ${APP_SECRET_NAME}"
echo "============================================="

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
export TF_VAR_firestore_database_name="${FIRESTORE_DATABASE_NAME}"
export TF_VAR_bucket_name="${BUCKET_NAME}"
export TF_VAR_app_secret_name="${APP_SECRET_NAME}"

# Configure Terraform state bucket
STATE_BUCKET="pklnd-terraform-state-${PROJECT_ID}"

# Create state bucket if it doesn't exist
if ! gsutil ls "gs://${STATE_BUCKET}" >/dev/null 2>&1; then
  echo "Creating Terraform state bucket: ${STATE_BUCKET}"
  gsutil mb -p "${PROJECT_ID}" -l "${REGION}" "gs://${STATE_BUCKET}"
  gsutil versioning set on "gs://${STATE_BUCKET}"
  echo "✓ State bucket created with versioning enabled"
else
  echo "✓ State bucket already exists: ${STATE_BUCKET}"
fi

# Use a separate state prefix for test infrastructure
terraform -chdir="${TF_DIR}" init -input=false \
  -backend-config="bucket=${STATE_BUCKET}" \
  -backend-config="prefix=infrastructure-test"
terraform -chdir="${TF_DIR}" apply -input=false -auto-approve "$@"

echo ""
echo "=== Test Infrastructure Setup Complete ==="
echo "Next steps:"
echo "1. If you haven't added test credentials, create the secret version:"
echo "   echo '{\"google_client_id\":\"...\",\"google_client_secret\":\"...\",\"ai_studio_api_key\":\"\"}' | \\"
echo "     gcloud secrets versions add ${APP_SECRET_NAME} --data-file=- --project=${PROJECT_ID}"
echo ""
echo "2. Deploy services to test environment:"
echo "   PROJECT_ID=${PROJECT_ID} ./scripts/terraform/deploy_to_test.sh"
echo "=========================================="
