#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}" || exit 1

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-us-central1}"
TERRAFORM_DIR="${TERRAFORM_DIR:-${REPO_ROOT}/infra/prod}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured via 'gcloud config set project'." >&2
  exit 1
fi

if [[ ! -d "${TERRAFORM_DIR}" ]]; then
  echo "Terraform directory ${TERRAFORM_DIR} not found; run infra/prod first." >&2
  exit 1
fi

if ! command -v terraform >/dev/null 2>&1; then
  echo "Terraform is required to read production outputs; install it or export the variables manually." >&2
fi

# Safely read a Terraform output if available.
tf_output() {
  local key="$1"
  if command -v terraform >/dev/null 2>&1; then
    terraform -chdir="${TERRAFORM_DIR}" output -raw "${key}" 2>/dev/null || true
  fi
}

# Pull defaults from Terraform state when present, falling back to standard production names.
GCS_BUCKET="${GCS_BUCKET:-$(tf_output bucket_name)}"
WEB_SERVICE_NAME="${WEB_SERVICE_NAME:-$(tf_output web_service_name)}"
RECEIPT_SERVICE_NAME="${RECEIPT_SERVICE_NAME:-$(tf_output receipt_service_name)}"
WEB_ARTIFACT_REPO="${WEB_ARTIFACT_REPO:-$(tf_output web_repository)}"
RECEIPT_ARTIFACT_REPO="${RECEIPT_ARTIFACT_REPO:-$(tf_output receipt_repository)}"
WEB_SA_NAME="${WEB_SA_NAME:-$(tf_output web_service_account_email)}"
RECEIPT_SA_NAME="${RECEIPT_SA_NAME:-$(tf_output receipt_service_account_email)}"
CONFIG_SECRET_NAME="${CONFIG_SECRET_NAME:-$(tf_output config_secret)}"

WEB_SERVICE_NAME="${WEB_SERVICE_NAME:-pklnd-web}"
RECEIPT_SERVICE_NAME="${RECEIPT_SERVICE_NAME:-pklnd-receipts}"
WEB_ARTIFACT_REPO="${WEB_ARTIFACT_REPO:-web}"
RECEIPT_ARTIFACT_REPO="${RECEIPT_ARTIFACT_REPO:-receipts}"
WEB_SA_NAME="${WEB_SA_NAME:-cloud-run-runtime}"
RECEIPT_SA_NAME="${RECEIPT_SA_NAME:-receipt-processor}"

if [[ -z "${GCS_BUCKET}" ]]; then
  echo "GCS_BUCKET is required; set it explicitly or ensure Terraform state exists in ${TERRAFORM_DIR}." >&2
  exit 1
fi

if ! gcloud storage buckets describe "gs://${GCS_BUCKET}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  echo "Bucket gs://${GCS_BUCKET} not found. Apply infra/prod or set GCS_BUCKET to an existing bucket." >&2
  exit 1
fi

echo "Deploying receipt processor to ${RECEIPT_SERVICE_NAME} in ${REGION}..."
PROJECT_ID="${PROJECT_ID}" \
REGION="${REGION}" \
RECEIPT_SERVICE_NAME="${RECEIPT_SERVICE_NAME}" \
RECEIPT_ARTIFACT_REPO="${RECEIPT_ARTIFACT_REPO}" \
RECEIPT_SA_NAME="${RECEIPT_SA_NAME}" \
GCS_BUCKET="${GCS_BUCKET}" \
CONFIG_SECRET_NAME="${CONFIG_SECRET_NAME:-pklnd-config}" \
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}" \
"${SCRIPT_DIR}/deploy_receipt_processor.sh"

RECEIPT_PROCESSOR_URL=$(gcloud run services describe "${RECEIPT_SERVICE_NAME}" \
  --project "${PROJECT_ID}" \
  --region "${REGION}" \
  --format "value(status.url)")

if [[ -z "${RECEIPT_PROCESSOR_URL}" ]]; then
  echo "Failed to detect receipt processor URL for ${RECEIPT_SERVICE_NAME}." >&2
  exit 1
fi

echo "Deploying web app to ${WEB_SERVICE_NAME} in ${REGION} using processor ${RECEIPT_PROCESSOR_URL}..."
PROJECT_ID="${PROJECT_ID}" \
REGION="${REGION}" \
SERVICE_NAME="${WEB_SERVICE_NAME}" \
ARTIFACT_REPO="${WEB_ARTIFACT_REPO}" \
SA_NAME="${WEB_SA_NAME}" \
GCS_BUCKET="${GCS_BUCKET}" \
RECEIPT_PROCESSOR_BASE_URL="${RECEIPT_PROCESSOR_URL}" \
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}" \
CONFIG_SECRET_NAME="${CONFIG_SECRET_NAME:-pklnd-config}" \
"${SCRIPT_DIR}/deploy_cloud_run.sh"

echo "Deployment complete."
echo "Web URL:       $(gcloud run services describe "${WEB_SERVICE_NAME}" --project "${PROJECT_ID}" --region "${REGION}" --format 'value(status.url)')"
echo "Processor URL: ${RECEIPT_PROCESSOR_URL}"
