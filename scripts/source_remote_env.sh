#!/usr/bin/env bash
# shellcheck disable=SC2034,SC1090

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  echo "❌ This script is meant to be sourced. Run 'source scripts/source_remote_env.sh'." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
APPLICATION_YML="${REPO_ROOT}/web/src/main/resources/application.yml"

if [[ ! -f "${APPLICATION_YML}" ]]; then
  echo "❌ Unable to locate application.yml at ${APPLICATION_YML}" >&2
  return 1
fi

read_yaml_default() {
  local key="$1"
  python3 - "$APPLICATION_YML" "$key" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
key = sys.argv[2]
pattern = re.compile(r"^\s*{}:\s*\$\{[^:]+:([^}}]*)}}".format(re.escape(key)), re.MULTILINE)
match = pattern.search(path.read_text(encoding="utf-8"))
if match:
    sys.stdout.write(match.group(1))
PY
}

DEFAULT_FIRESTORE_PROJECT_ID="$(read_yaml_default "project-id")"
DEFAULT_USERS_COLLECTION="$(read_yaml_default "users-collection")"
DEFAULT_RECEIPTS_COLLECTION="$(read_yaml_default "receipts-collection")"
DEFAULT_ROLE="$(read_yaml_default "default-role")"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
export FIRESTORE_ENABLED="${FIRESTORE_ENABLED:-true}"
export FIRESTORE_PROJECT_ID="${FIRESTORE_PROJECT_ID:-$DEFAULT_FIRESTORE_PROJECT_ID}"
export FIRESTORE_USERS_COLLECTION="${FIRESTORE_USERS_COLLECTION:-$DEFAULT_USERS_COLLECTION}"
export FIRESTORE_DEFAULT_ROLE="${FIRESTORE_DEFAULT_ROLE:-$DEFAULT_ROLE}"
unset FIRESTORE_EMULATOR_HOST

if [[ -z "${RECEIPT_FIRESTORE_PROJECT_ID:-}" ]]; then
  export RECEIPT_FIRESTORE_PROJECT_ID="${FIRESTORE_PROJECT_ID}"
fi
if [[ -z "${RECEIPT_FIRESTORE_COLLECTION:-}" ]]; then
  export RECEIPT_FIRESTORE_COLLECTION="${DEFAULT_RECEIPTS_COLLECTION}"
fi
if [[ -z "${GOOGLE_CLOUD_PROJECT:-}" ]]; then
  export GOOGLE_CLOUD_PROJECT="${FIRESTORE_PROJECT_ID}"
fi

if [[ -n "${FIRESTORE_CREDENTIALS_FILE:-}" ]]; then
  # shellcheck source=/dev/null
  source "${SCRIPT_DIR}/load_local_secrets.sh"
fi

if [[ -z "${FIRESTORE_PROJECT_ID:-}" ]]; then
  echo "⚠️  FIRESTORE_PROJECT_ID is empty. Set it before starting the application to target a remote Firestore project." >&2
fi

cat <<MSG
✅ Remote Firestore environment variables set for project '${FIRESTORE_PROJECT_ID:-<unset>}'
   Firestore emulator disabled; the application will use remote data.
MSG
