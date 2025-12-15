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
  echo "   After installation, run: gcloud components install beta" >&2
  echo "" >&2
  echo "   Alternative: Use Docker Compose instead" >&2
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

# Create data directory if it doesn't exist
mkdir -p "${DATA_DIR}"

cat <<MSG
▶️  Starting Firestore emulator for project "${PROJECT_ID}" on ${HOST}:${PORT}.
   Data directory: ${DATA_DIR}
   Use Ctrl+C to stop the emulator.
MSG

# Start the emulator
exec gcloud beta emulators firestore start \
  --project="${PROJECT_ID}" \
  --host-port="${HOST}:${PORT}" \
  --data-dir="${DATA_DIR}" \
  "${ADDITIONAL_ARGS[@]}"
