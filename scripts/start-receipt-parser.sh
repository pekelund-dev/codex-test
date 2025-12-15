#!/usr/bin/env bash
#
# Start the receipt parser service locally
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${REPO_ROOT}"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Starting Receipt Parser Service"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check if environment is configured
if [[ -z "${PROJECT_ID:-}" ]]; then
  echo -e "${YELLOW}⚠${NC} Environment variables not set!"
  echo "  Please run: source .env.local"
  echo ""
  exit 1
fi

echo -e "${GREEN}✓${NC} Environment configured for project: ${PROJECT_ID}"

# Check if AI credentials are available
if [[ -z "${AI_STUDIO_API_KEY:-}" && -z "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
  echo ""
  echo -e "${YELLOW}⚠${NC} No AI credentials configured"
  echo "  For Google AI Studio: export AI_STUDIO_API_KEY=your-key"
  echo "  For Vertex AI: export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json"
  echo ""
  echo "  Running in local-receipt-test profile (legacy parser only)"
  echo ""
  
  exec ./mvnw -pl receipt-parser -am spring-boot:run \
    -Dspring-boot.run.profiles=local-receipt-test
fi

echo ""
echo "Starting receipt parser service..."
echo "API available at: http://localhost:8080"
echo ""
echo "Test endpoints:"
echo "  GET  http://localhost:8080/api/parsers"
echo "  POST http://localhost:8080/api/parsers/{parser-id}/parse"
echo "  POST http://localhost:8080/local-receipts/parse (test profile)"
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Start the receipt parser
exec ./mvnw -pl receipt-parser -am spring-boot:run
