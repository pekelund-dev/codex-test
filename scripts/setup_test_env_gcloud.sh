#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-europe-north1}"
TEST_ENV_NAME="${TEST_ENV_NAME:-test}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured via 'gcloud config set project'." >&2
  exit 1
fi

BUCKET_NAME="pklnd-receipts-${TEST_ENV_NAME}-${PROJECT_ID}"
WEB_REPOSITORY="web-${TEST_ENV_NAME}"
RECEIPTS_REPOSITORY="receipts-${TEST_ENV_NAME}"
WEB_SERVICE_ACCOUNT="cloud-run-runtime-${TEST_ENV_NAME}"
RECEIPT_SERVICE_ACCOUNT="receipt-processor-${TEST_ENV_NAME}"

printf "\nEnabling core APIs in project %s...\n" "${PROJECT_ID}"
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  aiplatform.googleapis.com \
  storage.googleapis.com \
  firestore.googleapis.com \
  secretmanager.googleapis.com \
  --project "${PROJECT_ID}"

echo "\nCreating receipts bucket gs://${BUCKET_NAME} (region ${REGION})..."
if ! gcloud storage buckets describe "gs://${BUCKET_NAME}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://${BUCKET_NAME}" --location "${REGION}" --project "${PROJECT_ID}" --uniform-bucket-level-access
else
  echo "Bucket already exists; skipping creation."
fi

create_repo() {
  local repo_name="$1"
  if gcloud artifacts repositories describe "${repo_name}" --location "${REGION}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
    echo "Artifact Registry repository ${repo_name} already exists in ${REGION}."
    return
  fi
  gcloud artifacts repositories create "${repo_name}" \
    --repository-format=docker \
    --location "${REGION}" \
    --project "${PROJECT_ID}" \
    --description "pklnd ${TEST_ENV_NAME} containers (${repo_name})"
}

echo "\nCreating Artifact Registry repositories..."
create_repo "${WEB_REPOSITORY}"
create_repo "${RECEIPTS_REPOSITORY}"

create_sa() {
  local sa_id="$1"
  local display_name="$2"
  if gcloud iam service-accounts describe "${sa_id}@${PROJECT_ID}.iam.gserviceaccount.com" >/dev/null 2>&1; then
    echo "Service account ${sa_id} already exists."
    return
  fi
  gcloud iam service-accounts create "${sa_id}" --display-name "${display_name}" --project "${PROJECT_ID}"
}

echo "\nCreating service accounts..."
create_sa "${WEB_SERVICE_ACCOUNT}" "pklnd web runtime (${TEST_ENV_NAME})"
create_sa "${RECEIPT_SERVICE_ACCOUNT}" "Receipt processor runtime (${TEST_ENV_NAME})"

web_sa_email="${WEB_SERVICE_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"
receipt_sa_email="${RECEIPT_SERVICE_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com"

echo "\nGranting IAM roles..."
for role in roles/datastore.user roles/storage.objectAdmin; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member "serviceAccount:${web_sa_email}" \
    --role "${role}" \
    --quiet
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member "serviceAccount:${receipt_sa_email}" \
    --role "${role}" \
    --quiet
done

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member "serviceAccount:${receipt_sa_email}" \
  --role roles/aiplatform.user \
  --quiet

gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member "serviceAccount:${receipt_sa_email}" \
  --role roles/logging.logWriter \
  --quiet

gcloud storage buckets add-iam-policy-binding "gs://${BUCKET_NAME}" \
  --member "serviceAccount:${web_sa_email}" \
  --role roles/storage.objectAdmin \
  --project "${PROJECT_ID}" \
  --quiet || true

gcloud storage buckets add-iam-policy-binding "gs://${BUCKET_NAME}" \
  --member "serviceAccount:${receipt_sa_email}" \
  --role roles/storage.objectAdmin \
  --project "${PROJECT_ID}" \
  --quiet || true

echo "\nSummary (environment: ${TEST_ENV_NAME}):"
echo "  Project:                 ${PROJECT_ID}"
echo "  Region:                  ${REGION}"
echo "  Bucket:                  gs://${BUCKET_NAME}"
echo "  Web Artifact Registry:   ${WEB_REPOSITORY}"
echo "  Receipt Artifact Registry: ${RECEIPTS_REPOSITORY}"
echo "  Web service account:     ${web_sa_email}"
echo "  Receipt service account: ${receipt_sa_email}"
echo "\nProceed to deploy with scripts/deploy_test_env.sh after building the services."
