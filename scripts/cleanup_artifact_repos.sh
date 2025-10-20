#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-europe-north1}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured with 'gcloud config set project'." >&2
  exit 1
fi

WEB_SERVICE_NAME="${WEB_SERVICE_NAME:-pklnd-web}"
WEB_ARTIFACT_REPO="${WEB_ARTIFACT_REPO:-web}"
WEB_REGION="${WEB_REGION:-${REGION}}"

RECEIPT_SERVICE_NAME="${RECEIPT_SERVICE_NAME:-pklnd-receipts}"
RECEIPT_ARTIFACT_REPO="${RECEIPT_ARTIFACT_REPO:-receipts}"
RECEIPT_REGION="${RECEIPT_REGION:-${REGION}}"

cleanup_repository() {
  local service_name="$1"
  local artifact_repo="$2"
  local repo_region="$3"

  if [[ -z "$service_name" ]] || [[ -z "$artifact_repo" ]]; then
    return
  fi

  local image_resource="${repo_region}-docker.pkg.dev/${PROJECT_ID}/${artifact_repo}/${service_name}"

  if ! mapfile -t digests < <(gcloud artifacts docker images list "$image_resource" \
    --sort-by=~UPDATE_TIME \
    --format="get(digest)" 2>/dev/null); then
    echo "Unable to list images for ${image_resource}; skipping." >&2
    return
  fi

  declare -A seen
  local unique_digests=()
  local digest
  for digest in "${digests[@]}"; do
    if [[ -z "$digest" ]]; then
      continue
    fi
    if [[ -n "${seen[$digest]:-}" ]]; then
      continue
    fi
    seen["$digest"]=1
    unique_digests+=("$digest")
  done

  if (( ${#unique_digests[@]} <= 1 )); then
    echo "No old images to delete for ${image_resource}."
    return
  fi

  echo "Pruning $((${#unique_digests[@]} - 1)) image(s) from ${image_resource}..."
  local skip_first=true
  for digest in "${unique_digests[@]}"; do
    if [[ "$skip_first" == true ]]; then
      skip_first=false
      continue
    fi

    if ! gcloud artifacts docker images delete "${image_resource}@${digest}" \
      --quiet \
      --delete-tags; then
      echo "Failed to delete ${image_resource}@${digest}; continuing." >&2
    fi
  done
}

cleanup_repository "$WEB_SERVICE_NAME" "$WEB_ARTIFACT_REPO" "$WEB_REGION"
cleanup_repository "$RECEIPT_SERVICE_NAME" "$RECEIPT_ARTIFACT_REPO" "$RECEIPT_REGION"

