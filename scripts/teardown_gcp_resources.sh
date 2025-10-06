#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

# Teardown script for the Responsive Receipts infrastructure.
# Removes Cloud Run, Cloud Functions, IAM bindings, and optionally supporting
# resources while tolerating partially created or already deleted assets.

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-europe-north1}"
SERVICE_NAME="${SERVICE_NAME:-pklnd-web}"
SA_NAME="${SA_NAME:-cloud-run-runtime}"
DOMAIN="${DOMAIN:-pklnd.pekelund.dev}"
ARTIFACT_REPO="${ARTIFACT_REPO:-web}"
CLOUD_FUNCTION_NAME="${CLOUD_FUNCTION_NAME:-receiptProcessingFunction}"
GCS_BUCKET="${GCS_BUCKET:-}"
DELETE_SERVICE_ACCOUNTS="${DELETE_SERVICE_ACCOUNTS:-false}"
DELETE_ARTIFACT_REPO="${DELETE_ARTIFACT_REPO:-false}"

DEFAULT_FUNCTION_SA="receipt-parser@${PROJECT_ID}.iam.gserviceaccount.com"
FUNCTION_SA="${FUNCTION_SA:-$DEFAULT_FUNCTION_SA}"

if [[ -z "$PROJECT_ID" ]]; then
  echo "PROJECT_ID must be set before running the teardown script." >&2
  exit 1
fi

set -x

gcloud config set project "$PROJECT_ID"

# Remove custom domain mapping without failing if it is already gone.
if [[ -n "$DOMAIN" ]]; then
  if gcloud beta run domain-mappings describe "$DOMAIN" --region "$REGION" >/dev/null 2>&1; then
    gcloud beta run domain-mappings delete "$DOMAIN" \
      --region "$REGION" \
      --quiet
  else
    echo "Domain mapping ${DOMAIN} not found; skipping deletion."
  fi
fi

# Delete the Cloud Run service when present.
if gcloud run services describe "$SERVICE_NAME" --region "$REGION" --quiet >/dev/null 2>&1; then
  gcloud run services delete "$SERVICE_NAME" \
    --region "$REGION" \
    --quiet
else
  echo "Cloud Run service ${SERVICE_NAME} not found; skipping deletion."
fi

# Delete the Cloud Function if it exists.
if gcloud functions describe "$CLOUD_FUNCTION_NAME" --gen2 --region "$REGION" --quiet >/dev/null 2>&1; then
  gcloud functions delete "$CLOUD_FUNCTION_NAME" \
    --gen2 \
    --region "$REGION" \
    --quiet
else
  echo "Cloud Function ${CLOUD_FUNCTION_NAME} not found; skipping deletion."
fi

# Remove IAM bindings for the Cloud Function service account.
if [[ -n "$FUNCTION_SA" ]] && gcloud iam service-accounts describe "$FUNCTION_SA" --quiet >/dev/null 2>&1; then
  gcloud projects remove-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${FUNCTION_SA}" \
    --role="roles/datastore.user" \
    --quiet || true
  gcloud projects remove-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${FUNCTION_SA}" \
    --role="roles/aiplatform.user" \
    --quiet || true
  if [[ -n "$GCS_BUCKET" ]]; then
    gcloud storage buckets remove-iam-policy-binding "gs://${GCS_BUCKET}" \
      --member="serviceAccount:${FUNCTION_SA}" \
      --role="roles/storage.objectAdmin" \
      --quiet || true
  fi
else
  echo "Cloud Function service account ${FUNCTION_SA} not found; skipping IAM cleanup."
fi

# Remove IAM bindings for the Cloud Run runtime service account.
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
if gcloud iam service-accounts describe "$SA_EMAIL" --quiet >/dev/null 2>&1; then
  gcloud projects remove-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/run.invoker" \
    --quiet || true
  gcloud projects remove-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/datastore.user" \
    --quiet || true
  gcloud projects remove-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/secretmanager.secretAccessor" \
    --quiet || true
else
  echo "Cloud Run service account ${SA_EMAIL} not found; skipping IAM cleanup."
fi

# Optionally delete service accounts after bindings are removed.
if [[ "$DELETE_SERVICE_ACCOUNTS" == "true" ]]; then
  if gcloud iam service-accounts describe "$FUNCTION_SA" --quiet >/dev/null 2>&1; then
    gcloud iam service-accounts delete "$FUNCTION_SA" --quiet
  fi
  if gcloud iam service-accounts describe "$SA_EMAIL" --quiet >/dev/null 2>&1; then
    gcloud iam service-accounts delete "$SA_EMAIL" --quiet
  fi
fi

# Optionally delete the Artifact Registry repository (images inside will be lost).
if [[ "$DELETE_ARTIFACT_REPO" == "true" ]]; then
  if gcloud artifacts repositories describe "$ARTIFACT_REPO" --location "$REGION" >/dev/null 2>&1; then
    gcloud artifacts repositories delete "$ARTIFACT_REPO" \
      --location "$REGION" \
      --quiet
  else
    echo "Artifact Registry repository ${ARTIFACT_REPO} not found; skipping deletion."
  fi
fi

set +x

echo "✅ Teardown complete. Remaining resources (if any) are listed above."
