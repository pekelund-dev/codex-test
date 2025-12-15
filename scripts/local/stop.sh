#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_DIR="$ROOT_DIR/local"
ENV_FILE="$COMPOSE_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "⚠️  Hittade ingen lokal .env-fil i $COMPOSE_DIR. Använder standardvärden."
fi

cd "$COMPOSE_DIR"
echo "🛑 Stoppar lokala tjänster..."
docker compose --env-file "$ENV_FILE" down --remove-orphans

echo "✅ Alla lokala tjänster har stoppats."
