#!/usr/bin/env bash
#
# Start the web application locally
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${REPO_ROOT}"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Starting pklnd Web Application"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check if environment is configured
if [[ -z "${PROJECT_ID:-}" ]]; then
  echo -e "${YELLOW}⚠${NC} Environment variables not set!"
  echo "  Please run: source .env.local"
  echo "  Or source: source ./scripts/legacy/source_local_env.sh"
  echo ""
  exit 1
fi

echo -e "${GREEN}✓${NC} Environment configured for project: ${PROJECT_ID}"
echo -e "${GREEN}✓${NC} Firestore emulator: ${FIRESTORE_EMULATOR_HOST:-not configured}"
echo ""

# Check if Firestore emulator is running (if configured)
if [[ -n "${FIRESTORE_EMULATOR_HOST:-}" ]]; then
  IFS=":" read -r HOST PORT <<<"${FIRESTORE_EMULATOR_HOST}"
  if command -v nc >/dev/null 2>&1; then
    if nc -z "${HOST}" "${PORT}" 2>/dev/null; then
      echo -e "${GREEN}✓${NC} Firestore emulator is running"
    else
      echo -e "${YELLOW}⚠${NC} Firestore emulator might not be running on ${FIRESTORE_EMULATOR_HOST}"
      echo "  Start it with: ./scripts/start-firestore-emulator.sh"
    fi
  fi
fi

echo ""
echo "Starting web application..."
echo "Access at: http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Start the web application
exec ./mvnw -Pinclude-web -pl web -am spring-boot:run
