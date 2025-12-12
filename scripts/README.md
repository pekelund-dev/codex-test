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

## Legacy Scripts

Located in `scripts/legacy/`:

These are the original scripts that provide more granular control over the development environment.

### `start_firestore_emulator.sh`
Original Firestore emulator script with advanced configuration options.

### `source_local_env.sh`
Sets up environment variables for local development. Must be sourced:
```bash
source ./scripts/legacy/source_local_env.sh
```

### `load_local_secrets.sh`
Loads credentials from JSON files. Must be sourced:
```bash
export FIRESTORE_CREDENTIALS_FILE=$HOME/.config/pklnd/firestore.json
export GOOGLE_OAUTH_CREDENTIALS_FILE=$HOME/.config/pklnd/oauth-client.json
source ./scripts/legacy/load_local_secrets.sh
```

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
