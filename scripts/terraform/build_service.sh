#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS] SERVICE

Build a single Cloud Run service or both services for faster iteration.

ARGUMENTS:
  SERVICE                Service to build (web|receipt-parser|both)

OPTIONS:
  -d, --deploy          Deploy after building (only works with 'both')
  -p, --project ID      Google Cloud project ID
  -r, --region REGION   Deployment region (default: us-east1)
  --skip-cache          Skip Docker layer cache (slower but clean build)
  -h, --help            Show this help message

EXAMPLES:
  # Build only the web service (fastest for web-only changes)
  $(basename "$0") web

  # Build only the receipt processor
  $(basename "$0") receipt-parser

  # Build both services in parallel
  $(basename "$0") both

  # Build and deploy both services
  $(basename "$0") --deploy both

  # Build web service without cache (clean build)
  $(basename "$0") --skip-cache web

ENVIRONMENT VARIABLES:
  PROJECT_ID            Google Cloud project (can also use -p flag)
  REGION                Deployment region (can also use -r flag)

NOTES:
  - Single service builds (web or receipt-parser) do not support automatic
    deployment. Use these for quick iteration, then run deploy_services.sh
    when ready to deploy.
  - The --deploy flag only works with 'both' and is equivalent to running
    deploy_services.sh directly.
  
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
    # For skip-cache, build without cache but still use the build config for consistency
    echo "Building without cache (cache layers will be ignored)"
    # Create a temporary config that doesn't use cache
    local temp_config=$(mktemp)
    trap "rm -f ${temp_config}" RETURN
    cat > "${temp_config}" <<'EOF'
options:
  machineType: 'E2_HIGHCPU_8'
  logging: CLOUD_LOGGING_ONLY

steps:
  - name: gcr.io/cloud-builders/docker
    args:
      - build
      - '--file'
      - '${_DOCKERFILE}'
      - '--tag'
      - '${_IMAGE_URI}'
      - '--no-cache'
      - '.'

images:
  - '${_IMAGE_URI}'
EOF
    gcloud builds submit "${REPO_ROOT}" \
      --config "${temp_config}" \
      --substitutions "_IMAGE_URI=${image_uri},_DOCKERFILE=${dockerfile}" \
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
      echo ""
      echo "Note: Automatic deployment after build is not yet implemented."
      echo "To deploy this image, update the deployment configuration manually or run:"
      echo "  ${SCRIPT_DIR}/deploy_services.sh"
    fi
    ;;

  receipt-parser)
    receipt_image="${REGION}-docker.pkg.dev/${PROJECT_ID}/receipts/pklnd-receipts:${timestamp}"
    build_service "receipt-parser" "${receipt_image}" "receipt-parser/Dockerfile" "${REPO_ROOT}/receipt-parser/cloudbuild.yaml"

    if [[ "${DEPLOY}" == "true" ]]; then
      echo ""
      echo "Note: Automatic deployment after build is not yet implemented."
      echo "To deploy this image, update the deployment configuration manually or run:"
      echo "  ${SCRIPT_DIR}/deploy_services.sh"
    fi
    ;;

  both)
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

      # Wait for both processes and check their exit codes
      web_status=0
      receipt_status=0

      wait $web_pid || web_status=$?
      wait $receipt_pid || receipt_status=$?

      if [[ $web_status -ne 0 ]]; then
        echo "Error: Web build failed with status ${web_status}" >&2
        exit $web_status
      fi

      if [[ $receipt_status -ne 0 ]]; then
        echo "Error: Receipt processor build failed with status ${receipt_status}" >&2
        exit $receipt_status
      fi

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
