#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-us-east1}"
FIRESTORE_LOCATION="${FIRESTORE_LOCATION:-$REGION}"

if [[ -z "$PROJECT_ID" ]]; then
  echo "Error: PROJECT_ID environment variable is required"
  exit 1
fi

echo "Destroying infrastructure for project: $PROJECT_ID"
echo "Region: $REGION"
echo "Firestore location: $FIRESTORE_LOCATION"
echo ""
echo "WARNING: This will delete:"
echo "  - Firestore database and ALL data"
echo "  - Storage bucket and ALL receipts"
echo "  - Artifact Registry repositories and ALL images"
echo "  - Service accounts"
echo "  - Secret Manager secrets"
echo "  - IAM bindings"
echo ""
read -p "Are you sure? Type 'DELETE' to confirm: " confirm

if [[ "$confirm" != "DELETE" ]]; then
  echo "Aborted"
  exit 0
fi

cd "$(dirname "$0")/../.."

terraform -chdir=infra/terraform/infrastructure destroy \
  -var="project_id=$PROJECT_ID" \
  -var="region=$REGION" \
  -var="firestore_location=$FIRESTORE_LOCATION"
