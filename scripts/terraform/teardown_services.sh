#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-us-east1}"

if [[ -z "$PROJECT_ID" ]]; then
  echo "Error: PROJECT_ID environment variable is required"
  exit 1
fi

echo "Destroying deployment stack for project: $PROJECT_ID"
echo "Region: $REGION"
echo ""
echo "WARNING: This will delete all Cloud Run services."
read -p "Continue? (yes/no): " confirm

if [[ "$confirm" != "yes" ]]; then
  echo "Aborted"
  exit 0
fi

cd "$(dirname "$0")/../.."

terraform -chdir=infra/terraform/deployment destroy \
  -var="project_id=$PROJECT_ID" \
  -var="region=$REGION" \
  -var="bucket_name=dummy" \
  -var="google_client_id=dummy" \
  -var="google_client_secret=dummy" \
  -var="ai_studio_api_key=dummy"
