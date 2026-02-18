#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
INFRA_DIR="${REPO_ROOT}/infra/terraform/infrastructure"
DEPLOY_DIR="${REPO_ROOT}/infra/terraform/deployment"

PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
REGION=${REGION:-us-east1}
WEB_SERVICE_NAME=${WEB_SERVICE_NAME:-pklnd-web}
RECEIPT_SERVICE_NAME=${RECEIPT_SERVICE_NAME:-pklnd-receipts}
WEB_DOCKERFILE=${WEB_DOCKERFILE:-Dockerfile}
WEB_BUILD_CONTEXT=${WEB_BUILD_CONTEXT:-${REPO_ROOT}}
RECEIPT_DOCKERFILE=${RECEIPT_DOCKERFILE:-receipt-parser/Dockerfile}
CLOUD_BUILD_CONFIG=${CLOUD_BUILD_CONFIG:-receipt-parser/cloudbuild.yaml}
APP_SECRET_NAME=${APP_SECRET_NAME:-pklnd-app-config}
APP_SECRET_VERSION=${APP_SECRET_VERSION:-latest}

if [[ -z "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID must be set or configured in gcloud before deploying services." >&2
  exit 1
fi

if [[ ! -d "${WEB_BUILD_CONTEXT}" ]]; then
  echo "WEB_BUILD_CONTEXT ${WEB_BUILD_CONTEXT} was not found." >&2
  exit 1
fi

if [[ ! -f "${WEB_BUILD_CONTEXT%/}/${WEB_DOCKERFILE}" ]]; then
  echo "Dockerfile ${WEB_DOCKERFILE} not found in ${WEB_BUILD_CONTEXT}." >&2
  exit 1
fi

if [[ ! -f "${REPO_ROOT}/${CLOUD_BUILD_CONFIG}" ]]; then
  echo "Cloud Build configuration ${CLOUD_BUILD_CONFIG} was not found under the repository root." >&2
  exit 1
fi

bucket_name=${BUCKET_NAME:-}
web_repo=web
receipt_repo=receipts
web_sa="cloud-run-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
receipt_sa="receipt-processor@${PROJECT_ID}.iam.gserviceaccount.com"

tf_output_raw() {
  terraform -chdir="${INFRA_DIR}" output -raw "$1" 2>/dev/null | tr -d '\n' || true
}

bucket_name_from_tf=$(tf_output_raw bucket_name)
web_registry_from_tf=$(tf_output_raw web_artifact_registry)
receipt_registry_from_tf=$(tf_output_raw receipt_artifact_registry)
web_sa_from_tf=$(tf_output_raw web_service_account_email)
receipt_sa_from_tf=$(tf_output_raw receipt_service_account_email)
billing_alerts_topic_from_tf=$(tf_output_raw billing_alerts_topic)
pubsub_invoker_sa_from_tf=$(tf_output_raw pubsub_invoker_service_account)

if [[ -n "${web_registry_from_tf}" ]]; then
  web_repo_from_tf=${web_registry_from_tf##*/}
fi

if [[ -n "${receipt_registry_from_tf}" ]]; then
  receipt_repo_from_tf=${receipt_registry_from_tf##*/}
fi

bucket_name=${bucket_name:-${bucket_name_from_tf}}
web_repo=${web_repo_from_tf:-${web_repo}}
receipt_repo=${receipt_repo_from_tf:-${receipt_repo}}
web_sa=${web_sa_from_tf:-${web_sa}}
receipt_sa=${receipt_sa_from_tf:-${receipt_sa}}
pubsub_invoker_sa=${PUBSUB_INVOKER_SA:-${pubsub_invoker_sa_from_tf:-}}
billing_alerts_topic=${BILLING_ALERTS_TOPIC:-${billing_alerts_topic_from_tf:-}}

bucket_name=${bucket_name:-"pklnd-receipts-${PROJECT_ID}"}

timestamp=$(date +%Y%m%d-%H%M%S)
web_image_base="${REGION}-docker.pkg.dev/${PROJECT_ID}/${web_repo}/${WEB_SERVICE_NAME}"
receipt_image_base="${REGION}-docker.pkg.dev/${PROJECT_ID}/${receipt_repo}/${RECEIPT_SERVICE_NAME}"
web_image="${web_image_base}:${timestamp}"
receipt_image="${receipt_image_base}:${timestamp}"

if ! gcloud secrets describe "${APP_SECRET_NAME}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  echo "Secret ${APP_SECRET_NAME} not found in project ${PROJECT_ID}. Ensure infrastructure is applied first." >&2
  exit 1
fi

secret_json=$(gcloud secrets versions access "${APP_SECRET_VERSION}" --secret "${APP_SECRET_NAME}" --project "${PROJECT_ID}")

parse_json_field() {
  local json_payload="$1"
  local key="$2"

  if command -v jq >/dev/null 2>&1; then
    echo "${json_payload}" | jq -r --arg key "${key}" '.[$key] // empty'
  else
    echo "${json_payload}" | sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p"
  fi
}

GOOGLE_CLIENT_ID=$(parse_json_field "${secret_json}" "google_client_id")
GOOGLE_CLIENT_SECRET=$(parse_json_field "${secret_json}" "google_client_secret")
AI_STUDIO_API_KEY=$(parse_json_field "${secret_json}" "ai_studio_api_key")

if [[ -z "${GOOGLE_CLIENT_ID}" || -z "${GOOGLE_CLIENT_SECRET}" ]]; then
  echo "Unified secret must include google_client_id and google_client_secret" >&2
  exit 1
fi

tfvars_file=$(mktemp)
chmod 600 "${tfvars_file}"
trap 'rm -f "${tfvars_file}"' EXIT

ALLOW_UNAUTHENTICATED_WEB=${ALLOW_UNAUTHENTICATED_WEB:-true}
CUSTOM_DOMAIN=${CUSTOM_DOMAIN:-}

json_escape() {
  local raw_value="$1"

  if command -v jq >/dev/null 2>&1; then
    jq -Rn --arg v "${raw_value}" '$v'
  else
    printf '"%s"' "$(printf '%s' "${raw_value}" | sed 's/\\/\\\\/g; s/"/\\"/g')"
  fi
}

allow_unauth_value=$(printf '%s' "${ALLOW_UNAUTHENTICATED_WEB}" | tr '[:upper:]' '[:lower:]')
build_branch=${BRANCH_NAME:-$(git -C "${REPO_ROOT}" rev-parse --abbrev-ref HEAD 2>/dev/null || true)}
build_commit=${COMMIT_SHA:-$(git -C "${REPO_ROOT}" rev-parse HEAD 2>/dev/null || true)}

cat >"${tfvars_file}" <<EOF
project_id = $(json_escape "${PROJECT_ID}")
region = $(json_escape "${REGION}")
bucket_name = $(json_escape "${bucket_name}")
web_service_account_email = $(json_escape "${web_sa}")
receipt_service_account_email = $(json_escape "${receipt_sa}")
secret_name = $(json_escape "${APP_SECRET_NAME}")
google_client_id = $(json_escape "${GOOGLE_CLIENT_ID}")
google_client_secret = $(json_escape "${GOOGLE_CLIENT_SECRET}")
ai_studio_api_key = $(json_escape "${AI_STUDIO_API_KEY}")
web_image = $(json_escape "${web_image}")
receipt_image = $(json_escape "${receipt_image}")
web_service_name = $(json_escape "${WEB_SERVICE_NAME}")
receipt_service_name = $(json_escape "${RECEIPT_SERVICE_NAME}")
firestore_project_id = $(json_escape "${FIRESTORE_PROJECT_ID:-${PROJECT_ID}}")
gcs_project_id = $(json_escape "${GCS_PROJECT_ID:-${PROJECT_ID}}")
vertex_ai_project_id = $(json_escape "${VERTEX_AI_PROJECT_ID:-${PROJECT_ID}}")
vertex_ai_location = $(json_escape "${VERTEX_AI_LOCATION:-${REGION}}")
logging_project_id = $(json_escape "${LOGGING_PROJECT_ID:-${PROJECT_ID}}")
allow_unauthenticated_web = ${allow_unauth_value}
custom_domain = $(json_escape "${CUSTOM_DOMAIN}")
pubsub_invoker_service_account_email = $(json_escape "${pubsub_invoker_sa}")
billing_alerts_topic = $(json_escape "${billing_alerts_topic}")
EOF

echo "Building both images in parallel for faster deployment..."

# Start web image build in background
{
  echo "Building web image ${web_image}"
  gcloud builds submit "${WEB_BUILD_CONTEXT}" \
    --config "${REPO_ROOT}/cloudbuild.yaml" \
    --substitutions "_IMAGE_BASE=${web_image_base},_IMAGE_TAG=${timestamp},_DOCKERFILE=${WEB_DOCKERFILE},_GIT_BRANCH=${build_branch},_GIT_COMMIT=${build_commit}" \
    --project "${PROJECT_ID}" \
    --timeout=1200s
} &
WEB_BUILD_PID=$!

# Start receipt processor build in background
{
  echo "Building receipt processor image ${receipt_image}"
  gcloud builds submit "${REPO_ROOT}" \
    --config "${CLOUD_BUILD_CONFIG}" \
    --substitutions "_IMAGE_BASE=${receipt_image_base},_IMAGE_TAG=${timestamp},_DOCKERFILE=${RECEIPT_DOCKERFILE},_GIT_BRANCH=${build_branch},_GIT_COMMIT=${build_commit}" \
    --project "${PROJECT_ID}" \
    --timeout=1200s
} &
RECEIPT_BUILD_PID=$!

# Wait for both builds to complete
echo "Waiting for parallel builds to complete..."
wait $WEB_BUILD_PID
WEB_BUILD_EXIT=$?
if [ $WEB_BUILD_EXIT -ne 0 ]; then
  echo "Web image build failed with exit code $WEB_BUILD_EXIT" >&2
  # Note: Killing local process won't cancel the remote Cloud Build job
  # The remote build will continue running on GCP
  exit 1
fi

wait $RECEIPT_BUILD_PID
RECEIPT_BUILD_EXIT=$?
if [ $RECEIPT_BUILD_EXIT -ne 0 ]; then
  echo "Receipt processor image build failed with exit code $RECEIPT_BUILD_EXIT" >&2
  exit 1
fi

echo "Both images built successfully"

# Configure Terraform state bucket
STATE_BUCKET="pklnd-terraform-state-${PROJECT_ID}"

# Create state bucket if it doesn't exist
if ! gsutil ls "gs://${STATE_BUCKET}" >/dev/null 2>&1; then
  echo "Creating Terraform state bucket: ${STATE_BUCKET}"
  gsutil mb -p "${PROJECT_ID}" -l "${REGION}" "gs://${STATE_BUCKET}"
  gsutil versioning set on "gs://${STATE_BUCKET}"
  echo "✓ State bucket created with versioning enabled"
else
  echo "✓ State bucket already exists: ${STATE_BUCKET}"
fi

terraform -chdir="${DEPLOY_DIR}" init -input=false \
  -backend-config="bucket=${STATE_BUCKET}" \
  -backend-config="prefix=deployment"
terraform -chdir="${DEPLOY_DIR}" apply -input=false -auto-approve -var-file="${tfvars_file}" "$@"

# Clean up Cloud Build source cache
echo "Cleaning up Cloud Build source cache..."
gsutil -m rm -r "gs://${PROJECT_ID}_cloudbuild/source/**" 2>/dev/null || true

# Clean up old container images in Artifact Registry (keep last 3 timestamped images per service)
echo "Cleaning up old container images in Artifact Registry..."
cleanup_old_images() {
  local image_base="$1"
  
  # List all tags for this image, sorted by creation time (oldest first)
  local all_tags=$(gcloud artifacts docker tags list "${image_base}" \
    --format="get(tag)" \
    --sort-by="CREATE_TIME" 2>/dev/null || echo "")
  
  if [[ -z "${all_tags}" ]]; then
    echo "No images found for ${image_base}"
    return 0
  fi
  
  # Extract just the tag names from the full paths
  # Format: projects/.../tags/TAG_NAME -> TAG_NAME
  local tag_names=$(echo "${all_tags}" | sed 's|.*/tags/||')
  
  # Filter to only timestamped tags (format: YYYYMMDD-HHMMSS), skip 'latest' and 'buildcache'
  # Use grep with line-based matching
  local timestamped_tags=$(echo "${tag_names}" | grep -E '^[0-9]{8}-[0-9]{6}$' || true)
  
  # Remove empty lines and whitespace
  timestamped_tags=$(echo "${timestamped_tags}" | sed '/^[[:space:]]*$/d')
  
  if [[ -z "${timestamped_tags}" ]]; then
    echo "No timestamped images to clean up for ${image_base}"
    return 0
  fi
  
  # Count lines instead of words
  local total_count=$(echo "${timestamped_tags}" | wc -l)
  
  if [[ ${total_count} -le 3 ]]; then
    echo "Only ${total_count} timestamped image(s) found for ${image_base}, keeping all (threshold: 3)"
    return 0
  fi
  
  # Keep the 3 most recent, delete the rest
  # Calculate how many to delete
  local delete_count=$((total_count - 3))
  local tags_to_delete=$(echo "${timestamped_tags}" | head -n ${delete_count})
  
  # Ensure tags_to_delete is not empty before proceeding
  if [[ -n "${tags_to_delete}" ]] && [[ ! "${tags_to_delete}" =~ ^[[:space:]]*$ ]]; then
    echo "Deleting ${delete_count} old image(s) from ${image_base} (keeping last 3)..."
    while IFS= read -r tag; do
      if [[ -n "${tag}" ]]; then
        echo "  Deleting ${image_base}:${tag}"
        gcloud artifacts docker images delete "${image_base}:${tag}" --quiet --project="${PROJECT_ID}" 2>/dev/null || true
      fi
    done <<< "${tags_to_delete}"
  fi
}

# Clean up web and receipt-parser images
cleanup_old_images "${web_image_base}"
cleanup_old_images "${receipt_image_base}"

echo "Artifact cleanup complete"
