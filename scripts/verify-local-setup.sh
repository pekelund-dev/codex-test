#!/usr/bin/env bash
#
# Verify local development setup
# This script checks that all components are properly configured
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

print_success() {
  echo -e "${GREEN}✓${NC} $1"
}

print_error() {
  echo -e "${RED}✗${NC} $1"
}

print_warning() {
  echo -e "${YELLOW}⚠${NC} $1"
}

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Verifying Local Development Setup"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

all_ok=true

# Check files exist
echo "Checking required files..."

if [[ -f "local-setup.sh" ]]; then
  print_success "local-setup.sh exists"
else
  print_error "local-setup.sh not found"
  all_ok=false
fi

if [[ -f "docker-compose.yml" ]]; then
  print_success "docker-compose.yml exists"
else
  print_error "docker-compose.yml not found"
  all_ok=false
fi

if [[ -f ".env.local" ]]; then
  print_success ".env.local exists"
else
  print_warning ".env.local not found (run ./local-setup.sh to create it)"
fi

if [[ -f "scripts/start-web-app.sh" ]]; then
  print_success "scripts/start-web-app.sh exists"
else
  print_error "scripts/start-web-app.sh not found"
  all_ok=false
fi

if [[ -f "scripts/start-receipt-parser.sh" ]]; then
  print_success "scripts/start-receipt-parser.sh exists"
else
  print_error "scripts/start-receipt-parser.sh not found"
  all_ok=false
fi

if [[ -f "scripts/start-firestore-emulator.sh" ]]; then
  print_success "scripts/start-firestore-emulator.sh exists"
else
  print_error "scripts/start-firestore-emulator.sh not found"
  all_ok=false
fi

if [[ -f "scripts/stop-local-services.sh" ]]; then
  print_success "scripts/stop-local-services.sh exists"
else
  print_error "scripts/stop-local-services.sh not found"
  all_ok=false
fi

if [[ -f "docs/local-setup-guide.md" ]]; then
  print_success "docs/local-setup-guide.md exists"
else
  print_error "docs/local-setup-guide.md not found"
  all_ok=false
fi

echo ""
echo "Checking script permissions..."

for script in local-setup.sh scripts/start-*.sh scripts/stop-*.sh; do
  if [[ -x "$script" ]]; then
    print_success "$script is executable"
  else
    print_error "$script is not executable"
    all_ok=false
  fi
done

echo ""
echo "Checking .env.local configuration..."

if [[ -f ".env.local" ]]; then
  # Source and check key variables
  set +u  # Allow unset variables temporarily
  source .env.local
  set -u
  
  if [[ -n "${PROJECT_ID:-}" ]]; then
    print_success "PROJECT_ID is set to: $PROJECT_ID"
  else
    print_error "PROJECT_ID not set in .env.local"
    all_ok=false
  fi
  
  if [[ -n "${FIRESTORE_EMULATOR_HOST:-}" ]]; then
    print_success "FIRESTORE_EMULATOR_HOST is set to: $FIRESTORE_EMULATOR_HOST"
  else
    print_warning "FIRESTORE_EMULATOR_HOST not set"
  fi
  
  if [[ -n "${SPRING_PROFILES_ACTIVE:-}" ]]; then
    print_success "SPRING_PROFILES_ACTIVE is set to: $SPRING_PROFILES_ACTIVE"
  else
    print_warning "SPRING_PROFILES_ACTIVE not set"
  fi
fi

echo ""
echo "Checking Maven build..."

if ./mvnw -v >/dev/null 2>&1; then
  print_success "Maven wrapper works"
else
  print_error "Maven wrapper failed"
  all_ok=false
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [[ "$all_ok" == true ]]; then
  echo -e "${GREEN}✓ All checks passed!${NC}"
  echo ""
  echo "Your local setup is ready. Next steps:"
  echo "  1. Start Firestore emulator: ./scripts/start-firestore-emulator.sh"
  echo "  2. In a new terminal: source .env.local"
  echo "  3. Start web app: ./scripts/start-web-app.sh"
  echo ""
  exit 0
else
  echo -e "${RED}✗ Some checks failed!${NC}"
  echo ""
  echo "Please address the errors above and try again."
  echo ""
  exit 1
fi
