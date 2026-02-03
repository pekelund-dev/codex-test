# pklnd

pklnd is a Spring Boot application for maintaining a personal receipt archive focused on Swedish retailers such as ICA. It helps you upload, store, and organise proof of purchase so every grocery run and household buy is searchable online. The responsive web experience is secured with email/password sign-in and Google OAuth 2.0, presenting public and protected pages through a unified navigation.

## Features

- Spring Boot 3 with Java 21 and Maven.
- Secure personal accounts via Spring Security with hashed credentials in Firestore and optional Google OAuth 2.0 login.
- Responsive Thymeleaf interface powered by Bootstrap 5 for a smooth receipt overview on mobile and desktop.
- Receipt workspace tailored for ICA and other Swedish stores, supporting PDF/image uploads that land in Google Cloud Storage.
- Automatic categorisation, store detection, and chronological sorting so your personal receipts stay easy to find.
- Modular architecture with Spring Modulith keeping the web and receipt processing services isolated and verified.

## Getting started

### Quick Setup with Environment Script

For the fastest setup, use the provided environment script that configures all necessary Google Cloud settings and then run the
web module directly. Activate the `include-web` Maven profile whenever you build or run the web module so that it participates in
the multi-module reactor:

```bash
# Source the environment setup script
source setup-env.sh

# Start the application
./mvnw -Pinclude-web -pl web -am spring-boot:run
```

The `setup-env.sh` script automatically configures:
- Google Cloud authentication
- Firestore database connection
- Google Cloud Storage bucket access
- OAuth2 credentials
- Receipt processor service environment variables

### Manual Setup

1. Ensure you have Java 21 available in your environment.
2. Configure Google OAuth 2.0 credentials by exporting the variables before running the app (the Cloud Run deployment script requires these values):

   ```bash
   export GOOGLE_CLIENT_ID=your-google-client-id
   export GOOGLE_CLIENT_SECRET=your-google-client-secret
   ```

   > ℹ️ Google sign-in is enabled by activating the `oauth` Spring profile. When these variables are present the helper scripts
   > automatically append `oauth` to `SPRING_PROFILES_ACTIVE`. For manual runs add `SPRING_PROFILES_ACTIVE=local,oauth` (or
   > `prod,oauth` in production).

   Prefer the Terraform-based pipeline described in [Terraform deployment](docs/terraform-deployment.md) when targeting Google Cloud. It provisions the infrastructure and deploys both Cloud Run services with a single unified Secret Manager secret. The earlier bash helpers are still available under `scripts/legacy/` if you need to compare behaviour with the previous gcloud-driven flow.

3. Configure Firestore if you want to enable user self-registration (see [Firestore configuration](#firestore-configuration)).
4. (Optional) Configure Google Cloud Storage to enable the receipts upload page (see
   [Google Cloud Storage configuration](#google-cloud-storage-configuration)). The Cloud Run deployment
   automation expects `GCS_BUCKET` (and optionally `GCS_PROJECT_ID`) to be exported before you run it so
   uploads are routed to the right bucket. Leave `GCS_CREDENTIALS` unset when deploying to Google Cloud
   managed runtimes—the attached service account provides Application Default Credentials automatically.

5. Build and run the web application module (remember to enable the `include-web` profile):

   ```bash
./mvnw -Pinclude-web -pl web -am spring-boot:run
   ```

6. Navigate to <http://localhost:8080> to explore the experience.

### Frontend asset pipeline

The web module ships with a small Vite build that bundles and versions the JavaScript and CSS assets referenced by the
Thymeleaf templates. Install the Node dependencies and build the assets before running a production build:

```bash
cd web
npm install
npm run build
```

To validate frontend code quality locally, use the lint scripts:

```bash
npm run lint
```

### Release process

Releases follow semantic versioning (`MAJOR.MINOR.PATCH`) and are tagged in Git. To cut a release:

1. Decide the next version number (e.g. `1.2.0`).
2. Update module versions in `pom.xml` and any documentation references.
3. Commit the version bump with a message like `chore(release): 1.2.0`.
4. Tag the release and push it:

   ```bash
   git tag -a v1.2.0 -m "Release 1.2.0"
   git push origin main --tags
   ```

### Firestore indexes and backups

Review the Firestore index requirements and backup guidance before deploying to production:

- [Firestore indexes](docs/firestore-indexes.md)
- [Backup and retention strategy](docs/backup-retention.md)
- [Firestore migrations](docs/firestore-migrations.md)

### Firestore configuration

Firestore stores user profiles and receipt parsing output. Choose the setup style that suits your workflow:

- [Firestore configuration with the gcloud CLI](docs/gcp-setup-gcloud.md#configure-firestore-via-gcloud)
- [Firestore configuration with the Cloud Console](docs/gcp-setup-cloud-console.md#configure-firestore-in-the-console)
- [Local development without Google Cloud](docs/local-development.md)

Both guides walk through project creation, database provisioning, service accounts, and environment variables required by the Spring Boot application.

Use the `FIRESTORE_DATABASE_ID` environment variable to point the app at the Firestore database you provisioned (falls back to `FIRESTORE_DATABASE_NAME` when unset). Terraform defaults to a named database `receipts-db`; set the variable to `(default)` if you kept the primary database id instead.

For admin-triggered backups and restores, configure `FIRESTORE_BACKUP_BUCKET` (and optionally `FIRESTORE_BACKUP_PREFIX`) to
point at a Cloud Storage bucket dedicated to Firestore exports.

> 💡 When deploying to Cloud Run or any other Google-managed runtime, leave `FIRESTORE_CREDENTIALS` unset—the service account attached to the workload authenticates automatically via Application Default Credentials. Only download JSON keys for local development or third-party hosting.

If you already generated service-account keys or OAuth credentials, keep the JSON files outside of the repository (for example `~/.config/pklnd/`). Point `FIRESTORE_CREDENTIALS_FILE` and/or `GOOGLE_OAUTH_CREDENTIALS_FILE` at those files and source the helper to populate the runtime environment without copying secrets into shell history:

```bash
export FIRESTORE_CREDENTIALS_FILE="$HOME/.config/pklnd/firestore.json"
export GOOGLE_OAUTH_CREDENTIALS_FILE="$HOME/.config/pklnd/oauth-client.json"
source ./scripts/legacy/load_local_secrets.sh
```

The helper infers `FIRESTORE_CREDENTIALS=file:/...` and extracts `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` so subsequent Maven or deployment commands pick up the secure values automatically.

### Service account handling by environment

- **Cloud Run / Google-managed runtimes** – The deployment script (and the console walkthrough) attaches the `cloud-run-runtime` service account directly to the Cloud Run service. Google automatically exchanges that identity for short-lived tokens through [Application Default Credentials](https://cloud.google.com/docs/authentication/provide-credentials-adc), so the container never needs a JSON key file. Leave `FIRESTORE_CREDENTIALS` unset in these environments; the Firestore client uses the attached service account transparently.
- **Local development / other hosts** – Provide your own credentials via `FIRESTORE_CREDENTIALS_FILE` and source `./scripts/legacy/load_local_secrets.sh`, or point the application at the Firestore emulator with `scripts/legacy/source_local_env.sh`. These helpers export `FIRESTORE_CREDENTIALS=file:/…` only when you intentionally supply a downloaded key.
- **Receipt processor Cloud Run service** – The deployment helper provisions a dedicated service account, attaches it to the receipt processor, and grants Firestore, Vertex AI, and Cloud Storage permissions. Like the main web service, the managed runtime exchanges that identity for short-lived tokens so no JSON key files are required on Google Cloud.

### Google Cloud Storage configuration

The receipts workspace reads from a private Cloud Storage bucket. Follow one of the companion guides to provision the bucket and credentials:

- [Storage configuration with the gcloud CLI](docs/gcp-setup-gcloud.md#configure-cloud-storage-via-gcloud)
- [Storage configuration with the Cloud Console](docs/gcp-setup-cloud-console.md#configure-cloud-storage-in-the-console)

After completing either path, restart the application and visit <http://localhost:8080/receipts> to upload and view receipt files.

> 💡 When running on Cloud Run or other Google-managed platforms, rely on the runtime service account and
> leave `GCS_CREDENTIALS` unset. The application will fall back to Application Default Credentials if the
> configured resource is missing, allowing you to keep downloaded JSON keys strictly for local development.

### Receipt parsing Cloud Run service (Vertex AI Gemini)

The `receipt-parser` module now packages the receipt processor as a standalone Spring Boot web application that runs on Cloud Run. The web frontend calls this service after each successful upload, allowing the processor to download the receipt, extract structured data with Gemini, and persist the results to Firestore. The `web` module still hosts the interactive UI, while shared storage components live in the `core` module.

### Spring Modulith module map

Both Spring Boot applications (`web` and `receipt-parser`) enable [Spring Modulith](https://docs.spring.io/spring-modulith/reference/) to keep architectural boundaries explicit. Each major package is declared as an `@ApplicationModule`, and lightweight tests validate that the module graph has no forbidden dependencies.

| Module | Location | Purpose |
| --- | --- | --- |
| `dev.pekelund.pklnd.web` | `web` | MVC controllers and view adapters for the responsive UI. |
| `dev.pekelund.pklnd.firestore` | `web` | Firestore access for users and parsed receipts (declared as an open module so shared form DTOs can be reused without strict dependency checks). |
| `dev.pekelund.pklnd.receipts` | `web` | Receipt dashboard orchestration and Cloud Storage integration. |
| `dev.pekelund.pklnd.config` | `web` | Cross-cutting configuration shared by the web modules. |
| `dev.pekelund.pklnd.receiptparser` | `receipt-parser` | Receipt ingestion, extraction orchestration, and HTTP API surface. |
| `dev.pekelund.pklnd.receiptparser.legacy` | `receipt-parser` | Legacy parsing pipeline retained for comparison and fallbacks (marked as an open module because the modern extractor bridges to these classes). |
| `dev.pekelund.pklnd.receiptparser.local` | `receipt-parser` | Local developer tooling, including mock controllers and chat models. |
| `dev.pekelund.pklnd.storage` | `core` | Cloud Storage integration and abstractions shared across services. |
| `dev.pekelund.pklnd.receipts` | `core` | Domain constants consumed by both applications. |

Run the targeted Modulith verification tests to ensure boundaries stay intact:

```bash
# Core module scaffolding shared by both services
./mvnw -pl core test -Dtest=ModularityVerificationTests

# Web application modules
./mvnw -Pinclude-web -pl web -am test -Dtest=ModularityVerificationTests

# Receipt processor modules
./mvnw -pl receipt-parser -am test -Dtest=ModularityVerificationTests
```

### Feature packaging plan

The web module will move toward a package-by-feature structure so controllers, templates, and services are grouped by user-facing
capability instead of technical layers. The table below captures the current route ownership and the proposed feature packages.

| Routes / responsibilities | Current controller | Proposed package |
| --- | --- | --- |
| `/`, `/home`, `/about` | `HomeController` | `dev.pekelund.pklnd.web.home` |
| `/dashboard`, admin management actions under `/dashboard/admins` | `HomeController` | `dev.pekelund.pklnd.web.dashboard` |
| `/dashboard/statistics/**` (overview, users, stores, items, tags) | `HomeController` | `dev.pekelund.pklnd.web.statistics` |
| `/login`, `/register` | `HomeController`, `AuthController` | `dev.pekelund.pklnd.web.auth` |
| `/receipts`, `/receipts/search`, `/receipts/uploads`, `/receipts/overview`, `/receipts/errors`, `/receipts/**/reparse`, JSON endpoints under `/receipts/**` | `ReceiptController` | `dev.pekelund.pklnd.web.receipts` |
| `/api/categorization/**` | `CategorizationController` | `dev.pekelund.pklnd.web.categorization` |
| `/api/admin/categorization/**` | `CategorizationAdminController` | `dev.pekelund.pklnd.web.admin.categorization` |

Planned splits from `HomeController`:
- Home/about routes (`/`, `/home`, `/about`) will live in the `web.home` package.
- Dashboard routes (`/dashboard` and `/dashboard/admins/**`) will move into `web.dashboard`.
- Statistics routes (`/dashboard/statistics/**`) will move into `web.statistics`.
- Login route (`/login`) will move into `web.auth` alongside registration.

#### Quick Deployment

Use the Terraform automation for a streamlined setup with optimized build performance:

```bash
# 1) Seed the unified secret locally
cat > /tmp/pklnd-secret.json <<'JSON'
{"google_client_id":"your-client-id","google_client_secret":"your-client-secret","ai_studio_api_key":""}
JSON

# 2) Provision infrastructure (APIs, Artifact Registry, service accounts, Firestore, storage, and the unified secret)
PROJECT_ID=your-project APP_SECRET_FILE=/tmp/pklnd-secret.json ./scripts/terraform/apply_infrastructure.sh

# 3) Build and deploy both Cloud Run services with the values pulled from the single Secret Manager secret
PROJECT_ID=your-project ./scripts/terraform/deploy_services.sh
```

The deployment process has been optimized for speed and cost efficiency:
- Parallel image builds reducing total build time by ~50%
- Docker BuildKit with registry caching reducing rebuild time by ~60-80%
- Smaller build contexts reducing upload time by ~60-80%
- Higher CPU Cloud Build machines reducing compilation time by ~30-40%
- Automatic cleanup of old artifacts reducing storage costs by ~60-80%

The deployment script automatically removes old container images and Cloud Build archives to minimize storage costs. Only the last 3 timestamped images are retained per service, along with the `latest` tag and BuildKit cache.

For manual artifact cleanup or to customize retention settings, use:
```bash
PROJECT_ID=your-project ./scripts/terraform/cleanup_artifacts.sh
```

See the following documentation for more details:
- [Build Performance Optimizations](docs/build-performance-optimizations.md) — Detailed information about deployment speed improvements and artifact management.
- [Cloud Build Cost Analysis](docs/cloud-build-cost-analysis.md) — Cost analysis and budget planning.
- [Cost Summary](docs/cloud-build-cost-summary.md) — Summary of cloud build costs.
- [GitHub Actions CI/CD Guide](docs/github-actions-ci-cd-guide.md) — Using GitHub Actions as an alternative to Cloud Build.
- [GitHub Actions Secrets Setup](docs/github-actions-secrets-setup.md) — Complete guide for configuring GitHub Secrets and GCP permissions for deployment workflows.

The scripts keep infrastructure provisioning separate from application deployment while relying on one Secret Manager secret for OAuth and optional AI keys. Earlier gcloud-based helpers now live in `scripts/legacy/` for reference.

#### Teardown

Destroy the resources with Terraform when you want a clean slate:

```bash
PROJECT_ID=your-project terraform -chdir=infra/terraform/deployment destroy
PROJECT_ID=your-project terraform -chdir=infra/terraform/infrastructure destroy
```

If you prefer the previous bash-based cleanup workflow, the legacy teardown helper remains available under `scripts/legacy/`.

#### Manual Deployment

Select the deployment style you prefer:

- [Deploy the receipt processor with the gcloud CLI](docs/gcp-setup-gcloud.md#deploy-the-receipt-processing-service)
- [Deploy the receipt processor with the Cloud Console](docs/gcp-setup-cloud-console.md#deploy-the-receipt-processing-service)

Both documents describe prerequisites, metadata expectations, status updates, verification steps, and comprehensive troubleshooting guides for the Gemini-powered pipeline.

#### Local smoke testing

You can exercise the Cloud Run service locally without waiting for a new deployment by running it with Spring Boot:

1. Export credentials that allow the service to reach your Cloud Storage bucket, Firestore database, and Gemini provider. If you have a Google AI Studio key, set `AI_STUDIO_API_KEY` (and optionally `GOOGLE_AI_GEMINI_MODEL`). To call Vertex AI directly, continue supplying `GOOGLE_APPLICATION_CREDENTIALS`, `PROJECT_ID`, `VERTEX_AI_PROJECT_ID`, `VERTEX_AI_LOCATION`, `RECEIPT_FIRESTORE_COLLECTION`, `RECEIPT_FIRESTORE_ITEM_COLLECTION`, and `RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION`.
2. Start the service on a local port:

   ```bash
   ./mvnw -pl receipt-parser -am spring-boot:run
   ```

3. Upload a PDF to your receipts bucket (for example `gsutil cp test-receipt.pdf gs://your-receipts-bucket/receipts/sample-receipt.pdf`).
4. In another terminal, send the Cloud Storage finalize event payload to the locally running service:

   ```bash
   curl -X POST -H "Content-Type: application/json" \
     --data-binary @docs/sample-storage-event.json \
     http://localhost:8080/events/storage
   ```

Update `docs/sample-storage-event.json` with the bucket and object key you uploaded in step 3. The local instance uses the same code path as the deployed service, so Firestore documents and Gemini calls are executed exactly once the event is received.

#### Ad-hoc parsing REST API

When you run the receipt processor in its default profile it also exposes an HTTP API for on-demand parsing without persisting
results. Use it to experiment with specific parsers locally or in lower environments:

```bash
# list supported parser identifiers (hybrid, legacy, gemini)
curl http://localhost:8080/api/parsers | jq

# upload a PDF using one of the parser ids returned above
curl -F "file=@test-receipt.pdf" \
     http://localhost:8080/api/parsers/hybrid/parse | jq
```

The response echoes the parser that handled the request, the structured receipt data, and the raw Gemini response when
applicable. Invalid parser ids result in `404 Not Found`, non-PDF uploads return `400 Bad Request`, and extraction failures are
reported as `422 Unprocessable Entity` with an error payload.

#### Local parsing test server (no cloud dependencies)

When you only need to validate how the legacy PDF parser interprets a document, start the lightweight test server profile. It only
boots the legacy extractor, so no Firestore, Cloud Storage, or Vertex AI credentials are required:

```bash
# run from the repository root so the parent pom is picked up but only the
# receipt-parser module executes
./mvnw -pl receipt-parser -am spring-boot:run \
    -Dspring-boot.run.profiles=local-receipt-test
```

Once the server reports that Tomcat started on port 8080, submit a PDF for parsing with `curl` (replace the sample file with your
own receipt as needed):

```bash
curl -F "file=@test-receipt.pdf" http://localhost:8080/local-receipts/parse | jq
```

The response contains the structured data map emitted by the legacy parser along with the raw JSON string that mirrors what the
Cloud Run service would store. Errors such as unsupported file formats are returned with HTTP status `422` and a JSON payload with an
`error` message. Stop the server with `Ctrl+C` when you are finished.

### Fallback credentials

When Firestore integration is disabled the application falls back to an in-memory user store, but no accounts are created automatically. Configure explicit credentials for local testing by defining `firestore.fallback-users` entries in `web/src/main/resources/application.yml` (or through environment variables):

```yaml
firestore:
  fallback-users:
    - username: ${FIRESTORE_FALLBACK_ADMIN_USERNAME:}
      password: ${FIRESTORE_FALLBACK_ADMIN_PASSWORD:}
      roles:
        - ROLE_ADMIN
        - ROLE_USER
    - username: ${FIRESTORE_FALLBACK_USER_USERNAME:}
      password: ${FIRESTORE_FALLBACK_USER_PASSWORD:}
      roles:
        - ROLE_USER
```

Each configured password is encoded with the active `PasswordEncoder` on startup, so store only plaintext secrets in development configuration files and provide them securely through environment variables in production.

### Admin access

Users granted the `ROLE_ADMIN` authority can toggle between viewing only their own receipts and all uploaded receipts. The receipt overview, upload list, detail view, and item history pages include a scope switch when an administrator signs in.

For both password and OAuth sign-ins the application defers to Firestore for role assignments. When a new OAuth user authenticates, a document is created (or updated) in the configured users collection with their email address, name, and a default `ROLE_USER` role. With Firestore enabled, administrators can open the dashboard and use the **Administrator access** panel to add more admins by entering their email address (and an optional display name). The app updates the stored roles immediately, creating a new document when necessary, so the promotion applies to the very next sign-in. Existing admins also appear in the dashboard list with a quick remove control that revokes the `ROLE_ADMIN` authority while keeping their account intact.

If Firestore is disabled, fallback users continue to rely on the roles defined in configuration.

### OAuth 2.0 login

To enable Google sign-in, create OAuth credentials in the Google Cloud Console and configure the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables. The default callback URL is `http://localhost:8080/login/oauth2/code/google`.

## Project structure

- `core` – Shared storage services and configuration reused by both the web and receipt-parser modules.
- `web` – Spring MVC application with security, Firestore integration, templates, and static assets.
- `receipt-parser` – Cloud Run receipt processor that extracts receipts with Gemini and persists the output.

## Versioning

The application uses semantic versioning for Maven artifacts, git-based versioning for runtime metadata, and timestamp-based tagging for container images. See [docs/versioning.md](docs/versioning.md) for details on the versioning strategy and how to manage releases.

## License

This project is provided as a starter template and may be adapted to suit your needs.
