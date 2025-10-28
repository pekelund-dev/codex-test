#!/usr/bin/env bash
# shellcheck disable=SC2034,SC1090

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  echo "❌ This script is meant to be sourced. Run 'source scripts/source_local_env.sh'." >&2
  exit 1
fi

LOCAL_PROJECT_ID="${LOCAL_PROJECT_ID:-pklnd-local}"
LOCAL_FIRESTORE_HOST="${FIRESTORE_EMULATOR_HOST:-localhost:8085}"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
export FIRESTORE_ENABLED="${FIRESTORE_ENABLED:-true}"
export PROJECT_ID="${PROJECT_ID:-$LOCAL_PROJECT_ID}"
export FIRESTORE_PROJECT_ID="${FIRESTORE_PROJECT_ID:-$PROJECT_ID}"
export FIRESTORE_USERS_COLLECTION="${FIRESTORE_USERS_COLLECTION:-users}"
export FIRESTORE_DEFAULT_ROLE="${FIRESTORE_DEFAULT_ROLE:-ROLE_USER}"
export FIRESTORE_EMULATOR_HOST="${LOCAL_FIRESTORE_HOST}"
unset FIRESTORE_CREDENTIALS

export RECEIPT_FIRESTORE_COLLECTION="${RECEIPT_FIRESTORE_COLLECTION:-receiptExtractions}"
export RECEIPT_FIRESTORE_ITEM_COLLECTION="${RECEIPT_FIRESTORE_ITEM_COLLECTION:-receiptItems}"
export RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION="${RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION:-receiptItemStats}"
export GOOGLE_CLOUD_PROJECT="${GOOGLE_CLOUD_PROJECT:-$PROJECT_ID}"
export GCS_ENABLED="${GCS_ENABLED:-false}"
unset GCS_CREDENTIALS
unset GCS_PROJECT_ID
unset GCS_BUCKET

cat <<MSG
✅ Local environment variables set for project '${PROJECT_ID}'.
   Firestore emulator host: ${FIRESTORE_EMULATOR_HOST}
   Run './scripts/start_firestore_emulator.sh' in another terminal before starting the application.
MSG
