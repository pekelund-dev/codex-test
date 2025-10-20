#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"

echo "🚀 Starting Cloud Function deployment..."

if [[ -f "$REPO_ROOT/setup-env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$REPO_ROOT/setup-env.sh"
else
  echo "❌ setup-env.sh not found in $REPO_ROOT. Please create it before running this script." >&2
  exit 1
fi

: "${CLOUD_FUNCTION_NAME:=receiptProcessingFunction}"
: "${RECEIPT_PUBSUB_TOPIC:=receipt-processing}"
: "${RECEIPT_PUBSUB_PROJECT_ID:=${PROJECT_ID:-$(gcloud config get-value project)}}"

if [[ -z "${PROJECT_ID:-}" ]]; then
  PROJECT_ID="$(gcloud config get-value project)"
fi

if [[ -z "$PROJECT_ID" ]]; then
  echo "❌ PROJECT_ID must be set before deploying." >&2
  exit 1
fi

gcloud config set project "$PROJECT_ID" >/dev/null

if [[ -z "${GCS_BUCKET:-}" ]]; then
  echo "❌ GCS_BUCKET must be defined so the function can listen for uploads." >&2
  exit 1
fi

FUNCTION_SA=${FUNCTION_SA:-"receipt-parser@${PROJECT_ID}.iam.gserviceaccount.com"}

echo "👤 Using service account: $FUNCTION_SA"

echo "📍 Checking bucket region..."
BUCKET_REGION=$(gcloud storage buckets describe "gs://$GCS_BUCKET" --format="value(location)" 2>/dev/null || true)
if [[ -z "$BUCKET_REGION" ]]; then
  echo "❌ Could not determine region for gs://$GCS_BUCKET" >&2
  exit 1
fi

REGION="${BUCKET_REGION,,}"
echo "📍 Using region $REGION for function deployment"

echo "🔧 Enabling required Google Cloud APIs..."
gcloud services enable \
  cloudfunctions.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  eventarc.googleapis.com \
  storage.googleapis.com \
  pubsub.googleapis.com \
  run.googleapis.com \
  --quiet
echo "✅ APIs enabled successfully"

echo "👤 Ensuring service account exists..."
if ! gcloud iam service-accounts describe "$FUNCTION_SA" >/dev/null 2>&1; then
  gcloud iam service-accounts create "${FUNCTION_SA%%@*}" \
    --display-name="Receipt processing Cloud Function" \
    --project "$PROJECT_ID"
else
  echo "✅ Service account already exists"
fi

echo "🔐 Granting IAM permissions..."
gcloud storage buckets add-iam-policy-binding "gs://$GCS_BUCKET" \
  --member="serviceAccount:$FUNCTION_SA" \
  --role="roles/storage.objectViewer" \
  --quiet || true

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$FUNCTION_SA" \
  --role="roles/pubsub.publisher" \
  --quiet || true

PUBSUB_TOPIC_PATH="projects/${RECEIPT_PUBSUB_PROJECT_ID}/topics/${RECEIPT_PUBSUB_TOPIC}"
echo "📬 Ensuring Pub/Sub topic $PUBSUB_TOPIC_PATH exists..."
if ! gcloud pubsub topics describe "$PUBSUB_TOPIC_PATH" --format="value(name)" >/dev/null 2>&1; then
  gcloud pubsub topics create "$PUBSUB_TOPIC_PATH"
else
  echo "✅ Pub/Sub topic already exists"
fi

echo "🛠️  Building function module..."
./mvnw -q -pl function -am -DskipTests clean package

ENV_VARS="RECEIPT_PUBSUB_TOPIC=${RECEIPT_PUBSUB_TOPIC},RECEIPT_PUBSUB_PROJECT_ID=${RECEIPT_PUBSUB_PROJECT_ID}"

echo "🏗️  Deploying Cloud Function..."
gcloud functions deploy "$CLOUD_FUNCTION_NAME" \
  --gen2 \
  --runtime=java21 \
  --region="$REGION" \
  --source=. \
  --entry-point=dev.pekelund.pklnd.function.ReceiptEventPublisher \
  --memory=512Mi \
  --timeout=120s \
  --max-instances=5 \
  --service-account="$FUNCTION_SA" \
  --trigger-bucket="$GCS_BUCKET" \
  --set-env-vars="$ENV_VARS"

echo "🎉 Cloud Function deployed successfully!"
echo
echo "📋 Deployment Summary:"
echo "  Function Name: $CLOUD_FUNCTION_NAME"
echo "  Region: $REGION"
echo "  Trigger Bucket: gs://$GCS_BUCKET"
echo "  Pub/Sub Topic: $PUBSUB_TOPIC_PATH"
echo "  Service Account: $FUNCTION_SA"
