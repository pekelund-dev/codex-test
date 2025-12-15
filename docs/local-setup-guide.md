# Local Setup Guide

This guide provides step-by-step instructions for setting up and running pklnd entirely on your local machine, without needing a Google Cloud Platform account or deployment.

## Quick Start

If you just want to get started quickly:

```bash
# 1. Run the setup script
./local-setup.sh

# 2. Start Firestore emulator (choose one method)
./scripts/start-firestore-emulator.sh
# OR
docker compose up -d firestore

# 3. Load environment variables
source .env.local

# 4. Start the web application
./scripts/start-web-app.sh
```

Access the application at <http://localhost:8080>

Default credentials: `admin / admin123` or `user / user123`

### Verify Your Setup

To verify that everything is properly configured, run:

```bash
./scripts/verify-local-setup.sh
```

This script checks that all required files exist, scripts are executable, and the environment is configured correctly.

## Detailed Setup Instructions

### Prerequisites

Before you begin, ensure you have the following installed:

#### Required
- **Java 21 or higher** - Download from [Adoptium](https://adoptium.net/) or your package manager
- **Maven** - The project includes a Maven wrapper (`./mvnw`), so a system Maven install is optional

#### Optional (choose one for Firestore emulator)
- **Google Cloud SDK (gcloud)** - For running the Firestore emulator natively
  - Install from: <https://cloud.google.com/sdk/docs/install>
  - After installation, run: `gcloud components install beta`
- **Docker & Docker Compose** - Alternative method for Firestore emulator
  - Install from: <https://docs.docker.com/get-docker/>

### Step 1: Initial Setup

Run the setup script to verify prerequisites and create the local environment configuration:

```bash
./local-setup.sh
```

This script will:
- Check that Java 21+ is installed
- Verify the Maven wrapper is present
- Check for gcloud or Docker
- Create a `.env.local` file with default configuration

### Step 2: Start Firestore Emulator

The Firestore emulator provides a local database for user accounts and receipt data.

#### Option A: Using gcloud

```bash
./scripts/start-firestore-emulator.sh
```

**Requirements:**
- Google Cloud SDK (`gcloud`) must be installed
- Firestore emulator component must be installed

**Installing the Firestore Emulator Component:**

If you installed gcloud via package manager (apt/yum), use:
```bash
sudo apt-get install google-cloud-cli-firestore-emulator
# Or on Red Hat/CentOS:
# sudo yum install google-cloud-cli-firestore-emulator
```

If you installed gcloud via the install script, use:
```bash
gcloud components install cloud-firestore-emulator
```

**Note:** If you see "component manager is disabled" error, your gcloud was installed via package manager and you must use the apt-get/yum method above. The script will detect this and provide the correct installation command.

The emulator will:
- Start on `localhost:8085`
- Export data to `.local/firestore` directory on shutdown
- Import existing data on startup if available

To stop: Press `Ctrl+C` in the terminal where it's running.

#### Option B: Using Docker Compose (Recommended for CI/managed environments)

```bash
# Docker Compose v2 (recommended)
docker compose up -d firestore

# Or Docker Compose v1
docker-compose up -d firestore
```

**Advantages:**
- No gcloud components needed
- Works in any Docker-enabled environment
- Consistent across all platforms

The emulator will:
- Start on `localhost:8085`
- Export data to a Docker volume on shutdown
- Import existing data on startup if available

To stop:
```bash
docker compose down
# Or: docker-compose down
```

To view logs:
```bash
docker compose logs -f firestore
# Or: docker-compose logs -f firestore
```

### Step 3: Load Environment Variables

Before running the application, load the environment configuration:

```bash
source .env.local
```

This sets up:
- Project ID and Firestore configuration
- Emulator connection settings
- Collection names
- Spring profiles

**Note:** You'll need to run this command in each terminal session where you want to run the application or its services.

### Step 4: Start the Web Application

#### Using the wrapper script:

```bash
./scripts/start-web-app.sh
```

The script will:
- Check that environment variables are set
- Verify the Firestore emulator is running
- Start the web application
- Display the access URL

#### Manual start:

```bash
./mvnw -Pinclude-web -pl web -am spring-boot:run
```

### Step 5: Access the Application

Open your browser and navigate to:

```
http://localhost:8080
```

#### Default Credentials

When using the Firestore emulator without any pre-configured users, you can use these fallback credentials:

- **Admin account:** `admin / admin123`
- **User account:** `user / user123`

Once logged in, you can:
- Register new users
- Upload receipts (when Cloud Storage is configured)
- View the receipt dashboard

### Optional: Running the Receipt Parser Service

The receipt parser service extracts data from receipt images and PDFs using AI.

#### Basic setup (local parser only):

```bash
./scripts/start-receipt-parser.sh
```

This runs the service with the legacy PDF parser, which doesn't require any AI credentials.

#### With AI capabilities:

To enable Gemini-powered extraction, configure one of these options:

**Option 1: Google AI Studio**
```bash
export AI_STUDIO_API_KEY=your-api-key-here
./scripts/start-receipt-parser.sh
```

Get an API key from: <https://aistudio.google.com/app/apikey>

**Option 2: Vertex AI**
```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
export VERTEX_AI_PROJECT_ID=your-gcp-project
export VERTEX_AI_LOCATION=us-east1
./scripts/start-receipt-parser.sh
```

#### Testing the receipt parser

Once running, you can test the API:

```bash
# List available parsers
curl http://localhost:8081/api/parsers | jq

# Parse a PDF (replace with your receipt file)
curl -F "file=@test-receipt.pdf" \
     http://localhost:8081/api/parsers/hybrid/parse | jq
```

## Testing Receipt Parsing Locally with Firestore Storage

This section explains how to test the full receipt parsing workflow locally, storing results in the Firestore emulator **without needing Google Cloud Storage**.

### Overview

The standard workflow requires:
- **Receipt files** (PDFs/images) → Google Cloud Storage (GCS)
- **Receipt metadata** (parsed data) → Firestore

However, for local testing, you can bypass GCS and test parsing with Firestore storage using the `/local-receipts` endpoints. This is useful for:
- Testing parser changes without deploying to the cloud
- Verifying Firestore integration locally
- Developing and debugging without GCS setup

### Prerequisites

Before starting, ensure you have:

1. **Firestore emulator running:**
   ```bash
   ./scripts/start-firestore-emulator.sh
   # OR
   docker compose up -d firestore
   ```

2. **Environment loaded:**
   ```bash
   source .env.local
   ```
   
   Verify `FIRESTORE_EMULATOR_HOST` is set:
   ```bash
   echo $FIRESTORE_EMULATOR_HOST  # Should show: localhost:8085
   ```

### Method 1: Direct API Testing (Parse Only - No Storage)

The simplest approach for testing parser logic without storage:

```bash
# Start receipt parser in local-receipt-test mode (no AI credentials needed)
./scripts/start-receipt-parser.sh

# Test with legacy parser
curl -F "file=@your-receipt.pdf" \
     http://localhost:8081/local-receipts/parse | jq

# Test with codex parser
curl -F "file=@your-receipt.pdf" \
     http://localhost:8081/local-receipts/parse-codex | jq
```

**What this does:**
- Parses the receipt PDF and extracts structured data
- Returns JSON with `structuredData` and `rawResponse`
- **Does not** store anything in Firestore
- Useful for rapid parser testing and validation

**Example response:**
```json
{
  "structuredData": {
    "store": "ICA Supermarket",
    "date": "2025-08-20",
    "total": "156.50",
    "items": [...]
  },
  "rawResponse": "..."
}
```

### Method 2: Full Workflow with Real GCS (Stores in Firestore Emulator)

For testing the complete end-to-end flow including Firestore storage:

#### Step 1: Set up minimal GCS

Create a small GCS bucket (Google Cloud free tier provides 5GB):

```bash
# Create bucket (one-time setup)
gsutil mb -p your-project-id gs://your-test-receipts-bucket

# Get service account credentials and save to ~/.config/pklnd/storage.json
```

#### Step 2: Configure environment

Update `.env.local`:

```bash
# Enable GCS
export GCS_ENABLED=true
export GCS_PROJECT_ID=your-project-id
export GCS_BUCKET=your-test-receipts-bucket
export GCS_CREDENTIALS_FILE=$HOME/.config/pklnd/storage.json

# Keep Firestore emulator (not cloud Firestore)
export FIRESTORE_EMULATOR_HOST=localhost:8085

# Enable receipt processor integration
export RECEIPT_PROCESSOR_ENABLED=true
export RECEIPT_PROCESSOR_BASE_URL=http://localhost:8081
```

#### Step 3: Load credentials and restart services

```bash
# Load GCS credentials
source ./scripts/load-secrets.sh

# Restart services
source .env.local
./scripts/start-web-app.sh    # Terminal 1
./scripts/start-receipt-parser.sh  # Terminal 2
```

#### Step 4: Test the full workflow

1. **Via Web UI:**
   - Access http://localhost:8080
   - Login with your email (first user gets admin access)
   - Upload receipts through the UI
   - Files go to GCS, metadata to Firestore emulator

2. **Via API:**
   ```bash
   # Upload and trigger processing
   curl -F "file=@receipt.pdf" \
        -F "owner=test-user" \
        http://localhost:8080/api/receipts/upload
   ```

#### Step 5: Verify data in Firestore emulator

You can inspect the Firestore emulator data using the Firestore UI or by querying collections:

```bash
# Check if data was stored (using gcloud firestore CLI)
gcloud firestore --database-id=emulator collections list --project=pklnd-local

# Or inspect the exported data files
ls -la .local/firestore/
```

**What this achieves:**
- Receipt files stored in real GCS bucket (minimal cost)
- Receipt metadata stored in **local Firestore emulator** (completely local)
- Full workflow testing without deploying to the cloud
- Data persists locally in `.local/firestore/`

### Method 3: Manual Firestore Integration Testing

For advanced users who want to test Firestore write operations without the web UI:

```bash
# Start parser with Firestore access (not in local-receipt-test mode)
export AI_STUDIO_API_KEY=your-key  # Enables full profile
source .env.local
./scripts/start-receipt-parser.sh

# Now /api/parsers endpoints are available and can write to Firestore
curl http://localhost:8081/api/parsers | jq
```

**Note:** This requires either GCS setup or modifying the parser to accept direct file uploads while writing to Firestore.

### Comparing Methods

| Method | GCS Required | Firestore Storage | Use Case |
|--------|--------------|-------------------|----------|
| Method 1 (Direct API) | No | No | Quick parser logic testing |
| Method 2 (GCS + Emulator) | Yes (minimal) | Yes (emulator) | Full workflow testing locally |
| Method 3 (Manual) | Depends | Yes (emulator) | Advanced integration testing |

### Troubleshooting

**Parser returns 404 for `/api/parsers` endpoints:**
- You're in `local-receipt-test` profile (no AI credentials)
- Use `/local-receipts` endpoints instead, or add AI credentials

**Firestore data not persisting:**
- Check `.local/firestore/` directory exists
- Verify `FIRESTORE_EMULATOR_HOST` is set correctly
- Ensure emulator was started with `--export-on-exit` flag

**GCS upload fails:**
- Verify `GCS_CREDENTIALS_FILE` points to valid service account key
- Check bucket exists: `gsutil ls gs://your-bucket-name`
- Confirm service account has Storage Admin role

**Can't access Firestore data:**
- Data is stored in `.local/firestore/` directory
- Emulator exports data on shutdown (Ctrl+C)
- Use `--import-data` flag to restore data on next startup

### Viewing Stored Data

To see what's been stored in the Firestore emulator:

```bash
# View the data directory
ls -la .local/firestore/

# If using Docker, connect to the emulator UI
# (Note: The emulator doesn't have a built-in UI, but data is in files)

# Or use gcloud to query
gcloud firestore documents list --collection=receiptExtractions \
       --project=pklnd-local
```

### Summary

For most local development and testing:
- **Use Method 1** for quick parser validation (no setup needed)
- **Use Method 2** when you need to test Firestore storage integration
- Only use real cloud deployment when testing production features

This approach lets you develop and test the full application locally while keeping costs minimal and maintaining full control over your test environment.

## Configuring Google Cloud Services (Optional)

If you want to test with real Google Cloud services instead of the emulator:

### Firestore

1. Create a service account key in Google Cloud Console
2. Download the JSON key file to a safe location (e.g., `~/.config/pklnd/`)
3. Update `.env.local`:

```bash
# Comment out or remove these lines:
# export FIRESTORE_EMULATOR_HOST=localhost:8085

# Add these lines:
export FIRESTORE_CREDENTIALS_FILE=$HOME/.config/pklnd/firestore.json
export PROJECT_ID=your-gcp-project-id
export FIRESTORE_PROJECT_ID=your-gcp-project-id
```

4. Source the secrets loader:
```bash
source ./scripts/load-secrets.sh
```

### Google Cloud Storage

To enable receipt uploads:

```bash
export GCS_ENABLED=true
export GCS_PROJECT_ID=your-gcp-project-id
export GCS_BUCKET=your-receipts-bucket
export GCS_CREDENTIALS_FILE=$HOME/.config/pklnd/storage.json
```

### Google OAuth 2.0

To enable "Sign in with Google":

```bash
export GOOGLE_OAUTH_CREDENTIALS_FILE=$HOME/.config/pklnd/oauth-client.json
export SPRING_PROFILES_ACTIVE=local,oauth
```

Then load the credentials:
```bash
source ./scripts/load-secrets.sh
```

## Stopping Services

To stop all local services:

```bash
./scripts/stop-local-services.sh
```

This will:
- Stop the web application (if running on port 8080)
- Stop the receipt parser (if running on port 8081)
- Stop the Firestore emulator
- Stop Docker containers (if using docker-compose)

## Cleaning Up Data

### Firestore Emulator Data

To remove all local Firestore data:

```bash
rm -rf .local/firestore
```

### Docker Volumes

To remove Docker volumes:

```bash
docker-compose down -v
```

## Troubleshooting

### Quick Reference

**Firestore emulator component not installed?**
- Package manager install: `sudo apt-get install google-cloud-cli-firestore-emulator`
- Standard install: `gcloud components install cloud-firestore-emulator`
- Or use Docker: `docker compose up -d firestore` or `docker-compose up -d firestore` (no installation needed)

**Port already in use?**
- Run: `./scripts/stop-local-services.sh`

**Environment variables not set?**
- Run: `source .env.local`

### Port Already in Use

If you see an error about port 8080 being already in use:

```bash
# Find and kill the process using port 8080
lsof -ti:8080 | xargs kill -9

# Or use the stop script
./scripts/stop-local-services.sh
```

### Firestore Emulator Not Starting

**Error: "gcloud: command not found"**
- Install the Google Cloud SDK or use Docker instead

**Error: "cloud-firestore-emulator component not installed"**

This is the most common issue. The solution depends on how you installed gcloud:

If you see "component manager is disabled" error (gcloud installed via apt/yum):
```bash
# On Debian/Ubuntu:
sudo apt-get update
sudo apt-get install google-cloud-cli-firestore-emulator

# On Red Hat/CentOS:
sudo yum install google-cloud-cli-firestore-emulator
```

If you installed gcloud via install script:
```bash
gcloud components install cloud-firestore-emulator
```

After installation, run the start script again:
```bash
./scripts/start-firestore-emulator.sh
```

**Alternative: Use Docker (no component installation needed):**
```bash
# Docker Compose v2:
docker compose up -d firestore

# Docker Compose v1:
docker-compose up -d firestore
```

**Docker permission denied:**
```bash
sudo docker compose up -d firestore
# Or add your user to the docker group
```

### Maven Build Fails

**Missing dependencies:**
```bash
./mvnw clean install
```

**Java version issues:**
```bash
java -version  # Should show Java 21 or higher
```

Set JAVA_HOME if needed:
```bash
export JAVA_HOME=/path/to/java-21
```

### Application Won't Start

**Environment variables not set:**
- Make sure you ran `source .env.local`
- Check with: `echo $PROJECT_ID`

**Firestore connection issues:**
- Verify emulator is running: `curl http://localhost:8085`
- Check the emulator host: `echo $FIRESTORE_EMULATOR_HOST`

## Advanced Configuration

### Custom Ports

To run services on different ports:

```bash
# Web application
export SERVER_PORT=9090
./mvnw -Pinclude-web -pl web -am spring-boot:run -Dspring-boot.run.arguments=--server.port=9090

# Receipt parser
export PORT=9091
./mvnw -pl receipt-parser -am spring-boot:run -Dspring-boot.run.arguments=--server.port=9091

# Firestore emulator
export FIRESTORE_EMULATOR_HOST=localhost:9085
./scripts/start-firestore-emulator.sh
```

### Running Multiple Instances

To run both web and receipt-parser simultaneously:

```bash
# Terminal 1: Firestore emulator
./scripts/start-firestore-emulator.sh

# Terminal 2: Receipt parser on port 8081
source .env.local
export PORT=8081
./scripts/start-receipt-parser.sh

# Terminal 3: Web app on port 8080
source .env.local
export RECEIPT_PROCESSOR_ENABLED=true
export RECEIPT_PROCESSOR_BASE_URL=http://localhost:8081
./scripts/start-web-app.sh
```

### Development Profiles

The application supports multiple Spring profiles:

- `local` - Local development with emulator (default)
- `oauth` - Enable Google OAuth login
- `prod` - Production settings
- `local-receipt-test` - Receipt parser test mode (no AI required)

Combine profiles:
```bash
export SPRING_PROFILES_ACTIVE=local,oauth
```

## Next Steps

- Explore the [full documentation](../README.md)
- Learn about [GCP deployment](gcp-setup-gcloud.md)
- Review [architecture diagrams](system-architecture-diagrams.md)
- Check out [build optimizations](build-performance-optimizations.md)

## Getting Help

If you encounter issues:

1. Check this troubleshooting section
2. Review the logs in the terminal where services are running
3. Check the [local development guide](local-development.md) for advanced scenarios
4. Consult the main [README](../README.md) for general setup information
