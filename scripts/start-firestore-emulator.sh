#!/usr/bin/env bash
#
# Start Firestore emulator for local development
# This is a simpler wrapper around the legacy script
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Source the legacy script which has all the logic
exec "${REPO_ROOT}/scripts/legacy/start_firestore_emulator.sh" "$@"
