#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}" || exit 1

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-europe-north1}"
TEST_ENV_NAME="${TEST_ENV_NAME:-test}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured via 'gcloud config set project'." >&2
  exit 1
fi

RECEIPT_SERVICE_NAME="${RECEIPT_SERVICE_NAME:-pklnd-receipts-${TEST_ENV_NAME}}"
WEB_SERVICE_NAME="${WEB_SERVICE_NAME:-pklnd-web-${TEST_ENV_NAME}}"
RECEIPT_ARTIFACT_REPO="${RECEIPT_ARTIFACT_REPO:-receipts-${TEST_ENV_NAME}}"
WEB_ARTIFACT_REPO="${WEB_ARTIFACT_REPO:-web-${TEST_ENV_NAME}}"
RECEIPT_SA_NAME="${RECEIPT_SA_NAME:-receipt-processor-${TEST_ENV_NAME}}"
WEB_SA_NAME="${WEB_SA_NAME:-cloud-run-runtime-${TEST_ENV_NAME}}"
GCS_BUCKET="${GCS_BUCKET:-pklnd-receipts-${TEST_ENV_NAME}-${PROJECT_ID}}"
CONFIG_SECRET_NAME="${CONFIG_SECRET_NAME:-pklnd-config-${TEST_ENV_NAME}}"

if ! gcloud storage buckets describe "gs://${GCS_BUCKET}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  echo "Bucket gs://${GCS_BUCKET} not found. Run scripts/setup_test_env_gcloud.sh or apply infra/test-environment first." >&2
  exit 1
fi

echo "Deploying receipt processor to ${RECEIPT_SERVICE_NAME} in ${REGION}..."
PROJECT_ID="${PROJECT_ID}" \
REGION="${REGION}" \
RECEIPT_SERVICE_NAME="${RECEIPT_SERVICE_NAME}" \
RECEIPT_ARTIFACT_REPO="${RECEIPT_ARTIFACT_REPO}" \
RECEIPT_SA_NAME="${RECEIPT_SA_NAME}" \
GCS_BUCKET="${GCS_BUCKET}" \
CONFIG_SECRET_NAME="${CONFIG_SECRET_NAME}" \
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
CONFIG_SECRET_NAME="${CONFIG_SECRET_NAME}" \
"${SCRIPT_DIR}/deploy_cloud_run.sh"

echo "Deployment complete."
echo "Web URL:       $(gcloud run services describe "${WEB_SERVICE_NAME}" --project "${PROJECT_ID}" --region "${REGION}" --format 'value(status.url)')"
echo "Processor URL: ${RECEIPT_PROCESSOR_URL}"
