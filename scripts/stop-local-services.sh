#!/usr/bin/env bash
#
# Stop all local development services
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Stopping Local Development Services"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Stop any Spring Boot apps running on common ports
echo "Checking for running Spring Boot applications..."
for PORT in 8080 8081 8082; do
  if command -v lsof >/dev/null 2>&1; then
    PID=$(lsof -ti:$PORT 2>/dev/null || true)
    if [[ -n "$PID" ]]; then
      echo -e "${GREEN}✓${NC} Stopping process on port $PORT (PID: $PID)"
      kill "$PID" 2>/dev/null || true
      sleep 1
    fi
  elif command -v netstat >/dev/null 2>&1; then
    PID=$(netstat -tlnp 2>/dev/null | grep ":$PORT " | awk '{print $7}' | cut -d'/' -f1 || true)
    if [[ -n "$PID" ]]; then
      echo -e "${GREEN}✓${NC} Stopping process on port $PORT (PID: $PID)"
      kill "$PID" 2>/dev/null || true
      sleep 1
    fi
  fi
done

# Stop Firestore emulator if running
echo ""
echo "Checking for Firestore emulator..."
FIRESTORE_PORT="${FIRESTORE_EMULATOR_PORT:-8085}"
if command -v lsof >/dev/null 2>&1; then
  EMULATOR_PID=$(lsof -ti:$FIRESTORE_PORT 2>/dev/null || true)
  if [[ -n "$EMULATOR_PID" ]]; then
    echo -e "${GREEN}✓${NC} Stopping Firestore emulator (PID: $EMULATOR_PID)"
    kill "$EMULATOR_PID" 2>/dev/null || true
    sleep 1
  else
    echo "  No Firestore emulator found on port $FIRESTORE_PORT"
  fi
elif command -v netstat >/dev/null 2>&1; then
  EMULATOR_PID=$(netstat -tlnp 2>/dev/null | grep ":$FIRESTORE_PORT " | awk '{print $7}' | cut -d'/' -f1 || true)
  if [[ -n "$EMULATOR_PID" ]]; then
    echo -e "${GREEN}✓${NC} Stopping Firestore emulator (PID: $EMULATOR_PID)"
    kill "$EMULATOR_PID" 2>/dev/null || true
    sleep 1
  else
    echo "  No Firestore emulator found on port $FIRESTORE_PORT"
  fi
fi

# Stop Docker containers if docker-compose is being used
if [[ -f "${REPO_ROOT}/docker-compose.yml" ]]; then
  echo ""
  echo "Checking for Docker containers..."
  cd "${REPO_ROOT}"
  
  # Try docker compose v2 first, then fall back to docker-compose v1
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    if docker compose ps -q 2>/dev/null | grep -q .; then
      echo -e "${GREEN}✓${NC} Stopping Docker containers"
      docker compose down
    else
      echo "  No Docker containers running"
    fi
  elif command -v docker-compose >/dev/null 2>&1; then
    if docker-compose ps -q 2>/dev/null | grep -q .; then
      echo -e "${GREEN}✓${NC} Stopping Docker containers"
      docker-compose down
    else
      echo "  No Docker containers running"
    fi
  fi
fi

echo ""
echo -e "${GREEN}✓${NC} All local services stopped"
echo ""
echo "To clean up Firestore emulator data, run:"
echo "  rm -rf .local/firestore"
echo ""
