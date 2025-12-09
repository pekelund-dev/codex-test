#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS] SERVICE

Build and optionally deploy a single Cloud Run service for faster iteration.

ARGUMENTS:
  SERVICE                Service to build (web|receipt-parser|both)

OPTIONS:
  -d, --deploy          Deploy after building (default: build only)
  -p, --project ID      Google Cloud project ID
  -r, --region REGION   Deployment region (default: us-east1)
  --skip-cache          Skip Docker layer cache (slower but clean build)
  -h, --help            Show this help message

EXAMPLES:
  # Build only the web service
  $(basename "$0") web

  # Build and deploy the receipt processor
  $(basename "$0") --deploy receipt-parser

  # Build both services in parallel (same as deploy_services.sh)
  $(basename "$0") both

  # Build web service without cache
  $(basename "$0") --skip-cache web

ENVIRONMENT VARIABLES:
  PROJECT_ID            Google Cloud project (can also use -p flag)
  REGION                Deployment region (can also use -r flag)
  
EOF
}

SERVICE=""
DEPLOY=false
SKIP_CACHE=false
PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
REGION=${REGION:-us-east1}

while [[ $# -gt 0 ]]; do
  case $1 in
    -d|--deploy)
      DEPLOY=true
      shift
      ;;
    -p|--project)
      PROJECT_ID="$2"
      shift 2
      ;;
    -r|--region)
      REGION="$2"
      shift 2
      ;;
    --skip-cache)
      SKIP_CACHE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    web|receipt-parser|both)
      SERVICE="$1"
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${SERVICE}" ]]; then
  echo "Error: SERVICE argument is required" >&2
  usage >&2
  exit 1
fi

if [[ -z "${PROJECT_ID}" ]]; then
  echo "Error: PROJECT_ID must be set or configured in gcloud" >&2
  exit 1
fi

build_service() {
  local service=$1
  local image_uri=$2
  local dockerfile=$3
  local config=$4

  echo "Building ${service} image: ${image_uri}"

  if [[ "${SKIP_CACHE}" == "false" ]]; then
    gcloud builds submit "${REPO_ROOT}" \
      --config "${config}" \
      --substitutions "_IMAGE_URI=${image_uri},_DOCKERFILE=${dockerfile}" \
      --project "${PROJECT_ID}" \
      --timeout=1800s
  else
    # For skip-cache, use simple docker build without Cloud Build config
    echo "Building without cache (using direct docker build)"
    gcloud builds submit "${REPO_ROOT}" \
      --tag "${image_uri}" \
      --dockerfile "${dockerfile}" \
      --project "${PROJECT_ID}" \
      --timeout=1800s
  fi

  echo "✓ ${service} build complete"
}

timestamp=$(date +%Y%m%d-%H%M%S)

case "${SERVICE}" in
  web)
    web_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/web/pklnd-web:${timestamp}"
    build_service "web" "${web_image}" "Dockerfile" "${REPO_ROOT}/web/cloudbuild.yaml"
    
    if [[ "${DEPLOY}" == "true" ]]; then
      echo "Deploying web service..."
      WEB_IMAGE="${web_image}" "${SCRIPT_DIR}/deploy_services.sh"
    fi
    ;;
    
  receipt-parser)
    receipt_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/receipts/pklnd-receipts:${timestamp}"
    build_service "receipt-parser" "${receipt_image}" "receipt-parser/Dockerfile" "${REPO_ROOT}/receipt-parser/cloudbuild.yaml"
    
    if [[ "${DEPLOY}" == "true" ]]; then
      echo "Deploying receipt processor service..."
      RECEIPT_IMAGE="${receipt_image}" "${SCRIPT_DIR}/deploy_services.sh"
    fi
    ;;
    
  both)
    # Just call the main deploy script
    if [[ "${SKIP_CACHE}" == "true" ]]; then
      echo "Warning: --skip-cache not supported when building both services" >&2
    fi
    
    if [[ "${DEPLOY}" == "true" ]]; then
      "${SCRIPT_DIR}/deploy_services.sh"
    else
      echo "Building both services..."
      web_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/web/pklnd-web:${timestamp}"
      receipt_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/receipts/pklnd-receipts:${timestamp}"
      
      build_service "web" "${web_image}" "Dockerfile" "${REPO_ROOT}/web/cloudbuild.yaml" &
      web_pid=$!
      
      build_service "receipt-parser" "${receipt_image}" "receipt-parser/Dockerfile" "${REPO_ROOT}/receipt-parser/cloudbuild.yaml" &
      receipt_pid=$!
      
      wait $web_pid $receipt_pid
      echo "✓ All builds complete"
    fi
    ;;
esac

echo ""
echo "Build complete!"
if [[ "${DEPLOY}" == "false" ]]; then
  echo "To deploy this build, run:"
  echo "  ${SCRIPT_DIR}/deploy_services.sh"
fi
