# Scripts Directory

This directory contains helper scripts for local development and cloud deployment.

## Local Development Scripts

Located in the root `scripts/` directory:

### `start-firestore-emulator.sh`
Starts the Firestore emulator using gcloud. This is a wrapper around the legacy script.
```bash
./scripts/start-firestore-emulator.sh
```

### `start-web-app.sh`
Starts the web application with proper environment checks.
```bash
source .env.local
./scripts/start-web-app.sh
```

### `start-receipt-parser.sh`
Starts the receipt parser service. Automatically detects if AI credentials are available.
```bash
source .env.local
./scripts/start-receipt-parser.sh
```

### `stop-local-services.sh`
Stops all local development services including:
- Web application
- Receipt parser
- Firestore emulator
- Docker containers
```bash
./scripts/stop-local-services.sh
```

### `verify-local-setup.sh`
Verifies that the local development environment is properly configured.
```bash
./scripts/verify-local-setup.sh
```

### `load-secrets.sh`
Loads credentials from JSON files for GCP services. Must be sourced:
```bash
export FIRESTORE_CREDENTIALS_FILE=$HOME/.config/pklnd/firestore.json
export GOOGLE_OAUTH_CREDENTIALS_FILE=$HOME/.config/pklnd/oauth-client.json
export GCS_CREDENTIALS_FILE=$HOME/.config/pklnd/storage.json
source ./scripts/load-secrets.sh
```

This script extracts:
- Firestore credentials from service account JSON
- Google OAuth client ID and secret from OAuth credentials JSON
- Google Cloud Storage credentials from service account JSON

## Legacy Scripts

Located in `scripts/legacy/`:

These are the original scripts maintained for backward compatibility. The functionality has been incorporated into the scripts above.

## Terraform Scripts

Located in `scripts/terraform/`:

These scripts manage Google Cloud infrastructure and deployment using Terraform.

### `apply_infrastructure.sh`
Provisions GCP infrastructure (Firestore, Cloud Storage, service accounts, etc.)

### `deploy_services.sh`
Builds and deploys both web and receipt-parser services to Cloud Run

### `cleanup_artifacts.sh`
Removes old container images and build artifacts to reduce storage costs

### `destroy_infrastructure.sh` / `teardown_infrastructure.sh`
Destroys all provisioned infrastructure

See [Terraform deployment guide](../docs/terraform-deployment.md) for details.

## Quick Reference

**Start everything locally:**
```bash
# Terminal 1: Start emulator
./scripts/start-firestore-emulator.sh

# Terminal 2: Start web app
source .env.local
./scripts/start-web-app.sh

# Terminal 3 (optional): Start receipt parser
source .env.local
./scripts/start-receipt-parser.sh
```

**Stop everything:**
```bash
./scripts/stop-local-services.sh
```

**Deploy to Google Cloud:**
```bash
PROJECT_ID=your-project APP_SECRET_FILE=/tmp/pklnd-secret.json \
  ./scripts/terraform/apply_infrastructure.sh

PROJECT_ID=your-project ./scripts/terraform/deploy_services.sh
```
