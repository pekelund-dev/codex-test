#!/usr/bin/env bash
#
# Local setup script for pklnd
# This script helps you set up the entire local development environment
# including Firestore emulator and all necessary environment variables.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_header() {
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "$1"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
}

print_success() {
  echo -e "${GREEN}✓${NC} $1"
}

print_error() {
  echo -e "${RED}✗${NC} $1"
}

print_warning() {
  echo -e "${YELLOW}⚠${NC} $1"
}

print_info() {
  echo -e "  $1"
}

# Check prerequisites
check_prerequisites() {
  print_header "Checking Prerequisites"
  
  local all_ok=true
  
  # Check Java
  if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ "$JAVA_VERSION" -ge 21 ]]; then
      print_success "Java $JAVA_VERSION installed"
    else
      print_error "Java 21 or higher required (found Java $JAVA_VERSION)"
      all_ok=false
    fi
  else
    print_error "Java not found. Please install Java 21 or higher"
    all_ok=false
  fi
  
  # Check Maven wrapper
  if [[ -f "./mvnw" ]]; then
    print_success "Maven wrapper found"
  else
    print_error "Maven wrapper not found"
    all_ok=false
  fi
  
  # Check for gcloud (optional but recommended)
  if command -v gcloud >/dev/null 2>&1; then
    print_success "gcloud CLI installed (for Firestore emulator)"
  else
    print_warning "gcloud CLI not found. You can use Docker for Firestore emulator instead."
    print_info "Install from: https://cloud.google.com/sdk/docs/install"
  fi
  
  # Check for Docker (optional)
  if command -v docker >/dev/null 2>&1; then
    print_success "Docker installed (alternative for Firestore emulator)"
  else
    print_warning "Docker not found. Install it for easier Firestore emulator setup."
  fi
  
  if [[ "$all_ok" == false ]]; then
    print_error "Prerequisites check failed. Please install missing components."
    exit 1
  fi
  
  echo ""
}

# Setup local environment file
setup_local_env() {
  print_header "Setting Up Local Environment Configuration"
  
  local env_file=".env.local"
  
  if [[ -f "$env_file" ]]; then
    print_warning "Local environment file already exists: $env_file"
    read -p "Do you want to recreate it? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
      print_info "Keeping existing configuration"
      return
    fi
  fi
  
  cat > "$env_file" <<'EOF'
# Local Development Environment Configuration
# Source this file before running the application: source .env.local

# Project configuration
export PROJECT_ID=pklnd-local
export FIRESTORE_PROJECT_ID=pklnd-local
export GOOGLE_CLOUD_PROJECT=pklnd-local

# Spring profiles
export SPRING_PROFILES_ACTIVE=local

# Firestore configuration (using emulator)
export FIRESTORE_ENABLED=true
export FIRESTORE_EMULATOR_HOST=localhost:8085
export FIRESTORE_DATABASE_ID=receipts-db
export FIRESTORE_USERS_COLLECTION=users
export FIRESTORE_DEFAULT_ROLE=ROLE_USER

# Receipt collections
export RECEIPT_FIRESTORE_COLLECTION=receiptExtractions
export RECEIPT_FIRESTORE_ITEM_COLLECTION=receiptItems
export RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION=receiptItemStats

# Google Cloud Storage (disabled for local development)
export GCS_ENABLED=false

# Receipt processor (disabled for basic local setup)
# When enabled, set the base URL to the receipt parser service
export RECEIPT_PROCESSOR_ENABLED=false
export RECEIPT_PROCESSOR_BASE_URL=http://localhost:8081

# Optional: Uncomment and set these if you want to test with real Google Cloud services
# Then run: source ./scripts/load-secrets.sh to load credentials from JSON files
# export FIRESTORE_CREDENTIALS_FILE=$HOME/.config/pklnd/firestore.json
# export GOOGLE_OAUTH_CREDENTIALS_FILE=$HOME/.config/pklnd/oauth-client.json
# export GCS_CREDENTIALS_FILE=$HOME/.config/pklnd/storage.json
# Or set these directly:
# export GOOGLE_CLIENT_ID=your-client-id
# export GOOGLE_CLIENT_SECRET=your-client-secret
# export AI_STUDIO_API_KEY=your-api-key

# Fallback credentials for local testing (when Firestore is disabled)
export FIRESTORE_FALLBACK_ADMIN_USERNAME=admin
export FIRESTORE_FALLBACK_ADMIN_PASSWORD=admin123
export FIRESTORE_FALLBACK_USER_USERNAME=user
export FIRESTORE_FALLBACK_USER_PASSWORD=user123
EOF

  print_success "Created local environment file: $env_file"
  print_info "To use it, run: source .env.local"
  echo ""
}

# Show usage instructions
show_usage() {
  print_header "Next Steps"
  
  cat <<'USAGE'
Your local development environment is ready! Here's how to use it:

1. Start the Firestore emulator (choose one method):
   
   Option A - Using gcloud:
   ./scripts/start-firestore-emulator.sh
   
   If you get "component not installed" error, install it first:
   - Package manager install: sudo apt-get install google-cloud-cli-firestore-emulator
   - Standard install: gcloud components install cloud-firestore-emulator
   
   Option B - Using Docker (no installation needed):
   docker compose up -d firestore     # Docker Compose v2
   docker-compose up -d firestore     # Docker Compose v1

2. Load environment variables:
   source .env.local

3. Start the web application:
   ./scripts/start-web-app.sh
   
   Or manually:
   ./mvnw -Pinclude-web -pl web -am spring-boot:run

4. (Optional) Start the receipt parser service:
   ./scripts/start-receipt-parser.sh
   
   Or manually:
   ./mvnw -pl receipt-parser -am spring-boot:run

5. Access the application:
   Web UI: http://localhost:8080
   
   Default credentials (if Firestore emulator has no users):
   - Admin: admin / admin123
   - User: user / user123

6. Stop everything:
   ./scripts/stop-local-services.sh

For more details, see: docs/local-setup-guide.md

USAGE

  print_success "Setup complete!"
  echo ""
}

# Main execution
main() {
  print_header "pklnd Local Development Setup"
  print_info "This script will help you set up your local development environment"
  echo ""
  
  check_prerequisites
  setup_local_env
  show_usage
}

main "$@"
