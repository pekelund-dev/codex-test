#!/usr/bin/env bash
set -euo pipefail

# Cleanup script for removing old build artifacts from GCP to reduce storage costs
#
# This script removes:
# 1. Old timestamped container images from Artifact Registry (keeps last N images)
# 2. Cloud Build source archives from Cloud Storage
# 3. Optionally: BuildKit cache images (use with caution)
#
# Usage:
#   PROJECT_ID=your-project ./scripts/terraform/cleanup_artifacts.sh
#   PROJECT_ID=your-project KEEP_IMAGES=5 ./scripts/terraform/cleanup_artifacts.sh
#   PROJECT_ID=your-project CLEAN_CACHE=true ./scripts/terraform/cleanup_artifacts.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
REGION=${REGION:-us-east1}
WEB_SERVICE_NAME=${WEB_SERVICE_NAME:-pklnd-web}
RECEIPT_SERVICE_NAME=${RECEIPT_SERVICE_NAME:-pklnd-receipts}
KEEP_IMAGES=${KEEP_IMAGES:-3}  # Number of timestamped images to keep per service
CLEAN_CACHE=${CLEAN_CACHE:-false}  # Set to 'true' to also delete buildcache images

if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: PROJECT_ID must be set or configured in gcloud" >&2
  exit 1
fi

echo "╔════════════════════════════════════════════════════════════════════════╗"
echo "║                    GCP Artifact Cleanup Script                        ║"
echo "╚════════════════════════════════════════════════════════════════════════╝"
echo ""
echo "Project ID:           ${PROJECT_ID}"
echo "Region:               ${REGION}"
echo "Keep last N images:   ${KEEP_IMAGES}"
echo "Clean build cache:    ${CLEAN_CACHE}"
echo ""

# Clean up Cloud Build source archives
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1. Cleaning up Cloud Build source archives..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cloudbuild_bucket="gs://${PROJECT_ID}_cloudbuild"
if gsutil ls "${cloudbuild_bucket}" >/dev/null 2>&1; then
  echo "Removing source archives from ${cloudbuild_bucket}/source/..."
  gsutil -m rm -r "${cloudbuild_bucket}/source/**" 2>/dev/null || true
  echo "✓ Cloud Build source archives cleaned"
else
  echo "ℹ Cloud Build bucket not found (nothing to clean)"
fi

echo ""

# Function to clean up old container images
cleanup_old_images() {
  local service_name="$1"
  local image_base="$2"
  
  echo "Processing ${service_name}..."
  
  # List all tags for this image
  local all_tags=$(gcloud artifacts docker tags list "${image_base}" \
    --format="get(tag)" \
    --sort-by="~CREATE_TIME" \
    --project="${PROJECT_ID}" 2>/dev/null || echo "")
  
  if [[ -z "${all_tags}" ]]; then
    echo "  ℹ No images found"
    return 0
  fi
  
  # Filter to only timestamped tags (format: YYYYMMDD-HHMMSS)
  local timestamped_tags=$(echo "${all_tags}" | grep -E '^[0-9]{8}-[0-9]{6}$' || true)
  local total_timestamped=$(echo "${timestamped_tags}" | wc -l | tr -d ' ')
  
  if [[ -z "${timestamped_tags}" ]] || [[ "${total_timestamped}" -eq 0 ]]; then
    echo "  ℹ No timestamped images found"
  else
    echo "  Found ${total_timestamped} timestamped image(s)"
    
    # Calculate how many to delete
    local to_delete_count=$((total_timestamped - KEEP_IMAGES))
    
    if [[ ${to_delete_count} -gt 0 ]]; then
      local tags_to_delete=$(echo "${timestamped_tags}" | tail -n +$((KEEP_IMAGES + 1)))
      local deleted_count=0
      
      for tag in ${tags_to_delete}; do
        echo "  Deleting ${image_base}:${tag}"
        if gcloud artifacts docker images delete "${image_base}:${tag}" --quiet --project="${PROJECT_ID}" 2>/dev/null; then
          ((deleted_count++))
        fi
      done
      
      echo "  ✓ Deleted ${deleted_count} old image(s), kept last ${KEEP_IMAGES}"
    else
      echo "  ✓ All images are recent (keeping last ${KEEP_IMAGES})"
    fi
  fi
  
  # Clean up buildcache if requested
  if [[ "${CLEAN_CACHE}" == "true" ]]; then
    local cache_tags=$(echo "${all_tags}" | grep -E 'buildcache$' || true)
    if [[ -n "${cache_tags}" ]]; then
      echo "  Deleting build cache images..."
      for tag in ${cache_tags}; do
        echo "    Deleting ${image_base}:${tag}"
        gcloud artifacts docker images delete "${image_base}:${tag}" --quiet --project="${PROJECT_ID}" 2>/dev/null || true
      done
      echo "  ✓ Build cache deleted"
    fi
  fi
  
  echo ""
}

# Clean up web and receipt-parser images
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "2. Cleaning up Artifact Registry images..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

web_image_base="${REGION}-docker.pkg.dev/${PROJECT_ID}/web/${WEB_SERVICE_NAME}"
receipt_image_base="${REGION}-docker.pkg.dev/${PROJECT_ID}/receipts/${RECEIPT_SERVICE_NAME}"

cleanup_old_images "Web service" "${web_image_base}"
cleanup_old_images "Receipt-parser service" "${receipt_image_base}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✓ Artifact cleanup complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Tip: Run this script periodically to minimize storage costs."
echo "     Set KEEP_IMAGES to control how many old images to retain."
echo ""
