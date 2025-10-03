#!/bin/bash

# Cloud Function Deployment Script for Receipt Processing
# This script automates the deployment of the receiptProcessingFunction
# incorporating all the troubleshooting steps and best practices

set -e  # Exit on any error

echo "🚀 Starting Cloud Function deployment..."

# Source environment variables
source setup-env.sh

# Ensure CLOUD_FUNCTION_NAME is always set to a sensible default
: "${CLOUD_FUNCTION_NAME:=receiptProcessingFunction}"

if [ -z "$CLOUD_FUNCTION_NAME" ]; then
    echo "❌ CLOUD_FUNCTION_NAME must be set before deploying."
    exit 1
fi

# Check if we're in the correct project and make sure every component points to the same Firestore database
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project)}"
CURRENT_PROJECT=$(gcloud config get-value project)
if [ "$CURRENT_PROJECT" != "$PROJECT_ID" ]; then
    echo "⚠️  Switching to project: $PROJECT_ID"
    gcloud config set project "$PROJECT_ID"
fi

FUNCTION_SA=${FUNCTION_SA:-"receipt-parser@${PROJECT_ID}.iam.gserviceaccount.com"}
: "${RECEIPT_FIRESTORE_PROJECT_ID:=$PROJECT_ID}"
echo "👤 Using service account: $FUNCTION_SA"

# Check bucket region
echo "📍 Checking bucket region..."
BUCKET_REGION=$(gcloud storage buckets describe gs://$GCS_BUCKET --format="value(location)" 2>/dev/null || echo "")

if [ -z "$BUCKET_REGION" ]; then
    echo "❌ Could not determine bucket region for gs://$GCS_BUCKET"
    echo "Please ensure the bucket exists and you have access to it."
    exit 1
fi

echo "✅ Bucket $GCS_BUCKET is in region: $BUCKET_REGION"

# Use bucket region for function deployment
REGION=$(echo $BUCKET_REGION | tr '[:upper:]' '[:lower:]')
echo "📍 Using region $REGION for function deployment"

if [ -z "${VERTEX_AI_LOCATION:-}" ]; then
    VERTEX_AI_LOCATION="$REGION"
    echo "ℹ️  Defaulting Vertex AI location to $VERTEX_AI_LOCATION to match the deployment region"
else
    echo "📍 Using Vertex AI location $VERTEX_AI_LOCATION"
fi

FUNCTION_ENV_VARS="VERTEX_AI_PROJECT_ID=$PROJECT_ID,VERTEX_AI_LOCATION=$VERTEX_AI_LOCATION,VERTEX_AI_GEMINI_MODEL=gemini-2.0-flash,RECEIPT_FIRESTORE_PROJECT_ID=$RECEIPT_FIRESTORE_PROJECT_ID,RECEIPT_FIRESTORE_COLLECTION=receiptExtractions,SPRING_CLOUD_FUNCTION_DEFINITION=receiptProcessingFunction"

# Enable required APIs
echo "🔧 Enabling required Google Cloud APIs..."
gcloud services enable \
    cloudfunctions.googleapis.com \
    cloudbuild.googleapis.com \
    artifactregistry.googleapis.com \
    run.googleapis.com \
    aiplatform.googleapis.com \
    eventarc.googleapis.com \
    firestore.googleapis.com \
    storage.googleapis.com \
    pubsub.googleapis.com

echo "✅ APIs enabled successfully"

# Create service account if it doesn't exist
echo "👤 Setting up service account..."
SA_EXISTS=$(gcloud iam service-accounts list --filter="email:$FUNCTION_SA" --format="value(email)" | wc -l)

if [ "$SA_EXISTS" -eq 0 ]; then
    echo "Creating service account: $FUNCTION_SA"
    gcloud iam service-accounts create receipt-parser \
        --display-name="Receipt parsing Cloud Function"
else
    echo "✅ Service account already exists: $FUNCTION_SA"
fi

# Grant required permissions
echo "🔐 Granting IAM permissions..."

# Storage permissions
gcloud storage buckets add-iam-policy-binding "gs://$GCS_BUCKET" \
    --member="serviceAccount:$FUNCTION_SA" \
    --role="roles/storage.objectAdmin" \
    --quiet

# Firestore permissions
gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$FUNCTION_SA" \
    --role="roles/datastore.user" \
    --quiet

# Vertex AI permissions
gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$FUNCTION_SA" \
    --role="roles/aiplatform.user" \
    --quiet

echo "✅ IAM permissions configured"

# Ensure Firestore database exists
echo "🗄️  Setting up Firestore..."
if gcloud firestore databases describe --database="(default)" --format="value(name)" >/dev/null 2>&1; then
    echo "✅ Firestore database already exists"
else
    echo "Creating Firestore database..."
    gcloud firestore databases create --location=$REGION --type=firestore-native
fi

# Build and stage the function artifact so the deployment always uses the latest sources
echo "🛠️  Building function module..."
./mvnw -q -pl function -am -DskipTests clean package

# Deploy the Cloud Function using the full multi-module source
echo "🏗️  Deploying Cloud Function..."
echo "🧾 Cloud Function name: $CLOUD_FUNCTION_NAME"

gcloud functions deploy "$CLOUD_FUNCTION_NAME" \
    --gen2 \
    --runtime=java21 \
    --region="$REGION" \
    --source=. \
    --entry-point=org.springframework.cloud.function.adapter.gcp.GcfJarLauncher \
    --memory=1Gi \
    --timeout=300s \
    --max-instances=10 \
    --service-account="$FUNCTION_SA" \
    --trigger-bucket="$GCS_BUCKET" \
    --set-build-env-vars="MAVEN_BUILD_ARGUMENTS=-pl function -am -DskipTests -Dmdep.skip=true package" \
    --set-env-vars="$FUNCTION_ENV_VARS"

if [ $? -ne 0 ]; then
    echo "❌ Cloud Function deployment failed. Please check the error messages above."
    exit 1
fi

# Allow unauthenticated invocations for Eventarc triggers
echo "🔓 Configuring Cloud Run service for unauthenticated invocations..."
RUN_SERVICE_RESOURCE=$(gcloud functions describe "$CLOUD_FUNCTION_NAME" \
    --gen2 \
    --region="$REGION" \
    --format="value(serviceConfig.service)" 2>/dev/null || true)

if [ -z "$RUN_SERVICE_RESOURCE" ]; then
    RUN_SERVICE_NAME=$(echo "$CLOUD_FUNCTION_NAME" | tr '[:upper:]' '[:lower:]')
else
    RUN_SERVICE_NAME="${RUN_SERVICE_RESOURCE##*/}"
fi

if gcloud run services describe "$RUN_SERVICE_NAME" --region="$REGION" --quiet >/dev/null 2>&1; then
    gcloud run services add-iam-policy-binding "$RUN_SERVICE_NAME" \
        --region="$REGION" \
        --member="allUsers" \
        --role="roles/run.invoker" \
        --quiet
else
    echo "⚠️  Unable to configure unauthenticated access for Cloud Run service '$RUN_SERVICE_NAME'."
    echo "    The service may not expose an HTTP endpoint (e.g., event-triggered Cloud Function)."
fi

echo "🎉 Cloud Function deployed successfully!"

# Display useful information
echo ""
echo "📋 Deployment Summary:"
echo "  Function Name: $CLOUD_FUNCTION_NAME"
echo "  Region: $REGION"
echo "  Trigger: Cloud Storage bucket gs://$GCS_BUCKET"
echo "  Service Account: $FUNCTION_SA"
echo "  Vertex AI Location: $VERTEX_AI_LOCATION"
echo "  Vertex AI Model: gemini-2.0-flash"
echo ""
echo "📝 To view logs:"
echo "  gcloud functions logs read $CLOUD_FUNCTION_NAME --region=$REGION --gen2 --limit=10"
echo ""
echo "🧪 To test the function:"
echo "  Upload a PDF file to gs://$GCS_BUCKET/receipts/"
echo "  gsutil cp your-receipt.pdf gs://$GCS_BUCKET/receipts/"
echo ""
echo "🗂️  Sample receipt PDFs for local testing are available in function/src/test/resources/dev/pekelund/responsiveauth/files/"
echo "  (e.g., 'ICA Kvantum Malmborgs Caroli 2025-08-26.pdf', 'ICA Supermarket Hansa 2025-08-20.pdf')"
