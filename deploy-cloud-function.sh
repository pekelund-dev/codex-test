#!/bin/bash

# Cloud Function Deployment Script for Receipt Processing
# This script automates the deployment of the receiptProcessingFunction
# incorporating all the troubleshooting steps and best practices

set -e  # Exit on any error

echo "🚀 Starting Cloud Function deployment..."

# Source environment variables
source setup-env.sh

# Check if we're in the correct project
CURRENT_PROJECT=$(gcloud config get-value project)
EXPECTED_PROJECT="codex-test-473008"

if [ "$CURRENT_PROJECT" != "$EXPECTED_PROJECT" ]; then
    echo "⚠️  Switching to project: $EXPECTED_PROJECT"
    gcloud config set project $EXPECTED_PROJECT
fi

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
gcloud projects add-iam-policy-binding $EXPECTED_PROJECT \
    --member="serviceAccount:$FUNCTION_SA" \
    --role="roles/datastore.user" \
    --quiet

# Vertex AI permissions
gcloud projects add-iam-policy-binding $EXPECTED_PROJECT \
    --member="serviceAccount:$FUNCTION_SA" \
    --role="roles/aiplatform.user" \
    --quiet

echo "✅ IAM permissions configured"

# Ensure Firestore database exists
echo "🗄️  Setting up Firestore..."
FIRESTORE_EXISTS=$(gcloud firestore databases list --format="value(name)" --filter="name ~ 'projects/$EXPECTED_PROJECT/databases/(default)'" 2>/dev/null | wc -l)

if [ "$FIRESTORE_EXISTS" -eq 0 ]; then
    echo "Creating Firestore database..."
    gcloud firestore databases create --location=$REGION --type=firestore-native
else
    echo "✅ Firestore database already exists"
fi

# Build and deploy the function
echo "🏗️  Building and deploying Cloud Function..."

gcloud functions deploy "$CLOUD_FUNCTION_NAME" \
    --gen2 \
    --runtime=java21 \
    --region="$REGION" \
    --source=. \
    --entry-point=dev.pekelund.responsiveauth.function.ReceiptProcessingFunction \
    --memory=1Gi \
    --timeout=300s \
    --max-instances=10 \
    --service-account="$FUNCTION_SA" \
    --trigger-bucket="$GCS_BUCKET" \
    --set-env-vars="VERTEX_AI_PROJECT_ID=$EXPECTED_PROJECT,VERTEX_AI_LOCATION=$VERTEX_AI_LOCATION,VERTEX_AI_GEMINI_MODEL=gemini-2.0-flash,RECEIPT_FIRESTORE_COLLECTION=receiptExtractions"

if [ $? -ne 0 ]; then
    echo "❌ Cloud Function deployment failed. Please check the error messages above."
    exit 1
fi

# Allow unauthenticated invocations for Eventarc triggers
echo "🔓 Configuring Cloud Run service for unauthenticated invocations..."
gcloud run services add-iam-policy-binding "$CLOUD_FUNCTION_NAME" \
    --region="$REGION" \
    --member="allUsers" \
    --role="roles/run.invoker" \
    --quiet

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
