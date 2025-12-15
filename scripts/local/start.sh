#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/local"
ENV_FILE="$COMPOSE_DIR/.env"
DATA_DIR="$ROOT_DIR/.local/firestore"

if [ ! -f "$ENV_FILE" ]; then
  cat <<'ENV' > "$ENV_FILE"
FIRESTORE_PROJECT_ID=pklnd-local
FIRESTORE_DATABASE_ID=receipts-db
FIRESTORE_EMULATOR_PORT=8085
WEB_PORT=8080
PARSER_PORT=8081
ENV
  echo "📄 Skapade lokal miljöfil $ENV_FILE med standardvärden."
fi

mkdir -p "$DATA_DIR"

export DOCKER_BUILDKIT=1
cd "$COMPOSE_DIR"

echo "🚀 Startar lokala tjänster (Firestore-emulator, receipt-parser, web)..."
docker compose --env-file "$ENV_FILE" up -d --build

echo "✅ Alla lokala tjänster startas i bakgrunden. Använd scripts/local/stop.sh för att stänga ned."
