#!/usr/bin/env bash
#
# Start Firestore emulator for local development
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Check Bash version
if [[ "${BASH_VERSINFO[0]}" -lt 4 ]]; then
  echo "❌ Bash 4 or newer is required to run this script." >&2
  exit 1
fi

# Check if gcloud is installed
if ! command -v gcloud >/dev/null 2>&1; then
  echo "❌ gcloud must be installed to start the Firestore emulator." >&2
  echo "   Install from: https://cloud.google.com/sdk/docs/install" >&2
  echo "" >&2
  echo "   Alternative: Use Docker Compose instead" >&2
  echo "   Run: docker compose up -d firestore" >&2
  exit 1
fi

# Check if firestore emulator component is installed
if ! gcloud components list --filter="id:cloud-firestore-emulator" --format="value(state.name)" 2>/dev/null | grep -q "Installed"; then
  echo "❌ Firestore emulator component is not installed." >&2
  echo "" >&2
  echo "   To install it, run:" >&2
  echo "   gcloud components install cloud-firestore-emulator" >&2
  echo "" >&2
  echo "   Or if using apt-get:" >&2
  echo "   sudo apt-get install google-cloud-cli-firestore-emulator" >&2
  echo "" >&2
  echo "   Alternative: Use Docker Compose instead (no gcloud components needed)" >&2
  echo "   Run: docker compose up -d firestore" >&2
  exit 1
fi

# Configuration with defaults
HOST_PORT="${FIRESTORE_EMULATOR_HOST:-localhost:8085}"
PROJECT_ID="${FIRESTORE_EMULATOR_PROJECT_ID:-pklnd-local}"
DATA_DIR="${FIRESTORE_EMULATOR_DATA_DIR:-.local/firestore}"
ADDITIONAL_ARGS=()

# Parse host and port from HOST_PORT
IFS=":" read -r HOST PORT <<<"${HOST_PORT}"
if [[ -z "${HOST}" ]]; then
  HOST="localhost"
fi
if [[ -z "${PORT}" ]]; then
  PORT="8085"
fi

# For checking if port is in use, prefer localhost over 0.0.0.0
CHECK_HOST="${HOST}"
if [[ "${CHECK_HOST}" == "0.0.0.0" || "${CHECK_HOST}" == "*" ]]; then
  CHECK_HOST="127.0.0.1"
fi

# Check if emulator is already running on the port
python3 - <<PY
import socket, sys
host = "${CHECK_HOST}"
port = int("${PORT}")
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.settimeout(0.5)
try:
    sock.connect((host, port))
except OSError:
    sys.exit(1)
else:
    sys.exit(0)
finally:
    sock.close()
PY
STATUS=$?

if [[ $STATUS -eq 0 ]]; then
  cat <<MSG
⚠️  Firestore emulator already appears to be listening on ${HOST}:${PORT}.
   If this is intentional you can keep the existing instance running.
MSG
  exit 0
fi

# Create data directory if it doesn't exist (for exports)
mkdir -p "${DATA_DIR}"

# Prepare emulator arguments
EMULATOR_ARGS=(
  "--project=${PROJECT_ID}"
  "--host-port=${HOST}:${PORT}"
)

# Add export-on-exit if data directory is specified
if [[ -n "${DATA_DIR}" && "${DATA_DIR}" != "." ]]; then
  EMULATOR_ARGS+=("--export-on-exit=${DATA_DIR}")
  
  # Check for existing data to import
  EXPORT_FILE=$(find "${DATA_DIR}" -name "*.overall_export_metadata" 2>/dev/null | head -1 || true)
  if [[ -n "${EXPORT_FILE}" ]]; then
    EMULATOR_ARGS+=("--import-data=${EXPORT_FILE}")
    echo "📦 Importing existing data from ${EXPORT_FILE}"
  fi
fi

cat <<MSG
▶️  Starting Firestore emulator for project "${PROJECT_ID}" on ${HOST}:${PORT}.
   Data will be exported to: ${DATA_DIR}
   Use Ctrl+C to stop the emulator.
MSG

# Start the emulator
exec gcloud beta emulators firestore start "${EMULATOR_ARGS[@]}" "${ADDITIONAL_ARGS[@]}"
