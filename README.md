# ResponsiveAuthApp

ResponsiveAuthApp is a Spring Boot starter application showcasing a responsive web experience secured with email/password and Google OAuth 2.0 authentication. The UI is built with Thymeleaf and Bootstrap and demonstrates how to surface public and secured pages behind a unified navigation experience.

## Features

- Spring Boot 3 with Java 21 and Maven.
- Email/password registration handled by Spring Security with credentials hashed and user profiles stored in Cloud Firestore.
- Spring Security configuration with form login and Google OAuth 2.0 login entry point.
- Responsive layout powered by Bootstrap 5, including a splash screen and persistent top navigation bar with user avatar/initials.
- Sample Thymeleaf pages for unauthenticated and authenticated visitors.
- Ready-to-style components and custom CSS for cohesive branding.
- Optional receipts workspace with drag-and-drop uploads that sync to Google Cloud Storage.

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
- Cloud Function environment variables

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

### Firestore configuration

Firestore stores user profiles and receipt parsing output. Choose the setup style that suits your workflow:

- [Firestore configuration with the gcloud CLI](docs/gcp-setup-gcloud.md#configure-firestore-via-gcloud)
- [Firestore configuration with the Cloud Console](docs/gcp-setup-cloud-console.md#configure-firestore-in-the-console)
- [Local development without Google Cloud](docs/local-development.md)

Both guides walk through project creation, database provisioning, service accounts, and environment variables required by the Spring Boot application.

> 💡 When deploying to Cloud Run or any other Google-managed runtime, leave `FIRESTORE_CREDENTIALS` unset—the service account attached to the workload authenticates automatically via Application Default Credentials. Only download JSON keys for local development or third-party hosting.

If you already generated service-account keys or OAuth credentials, keep the JSON files outside of the repository (for example `~/.config/responsive-auth/`). Point `FIRESTORE_CREDENTIALS_FILE` and/or `GOOGLE_OAUTH_CREDENTIALS_FILE` at those files and source the helper to populate the runtime environment without copying secrets into shell history:

```bash
export FIRESTORE_CREDENTIALS_FILE="$HOME/.config/responsive-auth/firestore.json"
export GOOGLE_OAUTH_CREDENTIALS_FILE="$HOME/.config/responsive-auth/oauth-client.json"
source ./scripts/load_local_secrets.sh
```

The helper infers `FIRESTORE_CREDENTIALS=file:/...` and extracts `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` so subsequent Maven or deployment commands pick up the secure values automatically.

### Service account handling by environment

- **Cloud Run / Google-managed runtimes** – The deployment script (and the console walkthrough) attaches the `cloud-run-runtime` service account directly to the Cloud Run service. Google automatically exchanges that identity for short-lived tokens through [Application Default Credentials](https://cloud.google.com/docs/authentication/provide-credentials-adc), so the container never needs a JSON key file. Leave `FIRESTORE_CREDENTIALS` unset in these environments; the Firestore client uses the attached service account transparently.
- **Local development / other hosts** – Provide your own credentials via `FIRESTORE_CREDENTIALS_FILE` and source `./scripts/load_local_secrets.sh`, or point the application at the Firestore emulator with `scripts/source_local_env.sh`. These helpers export `FIRESTORE_CREDENTIALS=file:/…` only when you intentionally supply a downloaded key.
- **Cloud Function** – The deployment script now grants Pub/Sub publish access to the function’s runtime identity. Like Cloud Run, the managed function never needs a downloaded key unless you run it on your own infrastructure.

### Google Cloud Storage configuration

The receipts workspace reads from a private Cloud Storage bucket. Follow one of the companion guides to provision the bucket and credentials:

- [Storage configuration with the gcloud CLI](docs/gcp-setup-gcloud.md#configure-cloud-storage-via-gcloud)
- [Storage configuration with the Cloud Console](docs/gcp-setup-cloud-console.md#configure-cloud-storage-in-the-console)

After completing either path, restart the application and visit <http://localhost:8080/receipts> to upload and view receipt files.

> 💡 When running on Cloud Run or other Google-managed platforms, rely on the runtime service account and
> leave `GCS_CREDENTIALS` unset. The application will fall back to Application Default Credentials if the
> configured resource is missing, allowing you to keep downloaded JSON keys strictly for local development.

### Receipt ingestion pipeline (Cloud Function + Pub/Sub)

The `function` module now acts as a lightweight bridge: it listens for finalize events from the receipts bucket, enriches the payload, and publishes a `ReceiptProcessingMessage` to Pub/Sub. The bridge is implemented with the Functions Framework only—no Spring context is loaded—so the cold-start footprint stays minimal. The Cloud Run–hosted web application receives those messages through a Pub/Sub push subscription and performs the actual parsing and Firestore persistence.

#### Quick deployment

Use the deployment helper to provision the Pub/Sub topic, build the function, and deploy it with the correct entry point and environment variables:

```bash
# Deploy the Cloud Function and ensure the Pub/Sub topic exists
./scripts/deploy_cloud_function.sh
```

The script automatically:
- Enables the Cloud Functions, Pub/Sub, Eventarc, and Cloud Storage APIs
- Creates (or reuses) the service account with the required Pub/Sub publisher permissions
- Detects the storage bucket region so the function is deployed in the correct location
- Builds the function module and deploys it as a Java 21 second-generation function
- Ensures the Pub/Sub topic exists and injects the topic configuration via environment variables

After the function is deployed, run `./scripts/deploy_cloud_run.sh` to build and deploy the web application. The script wires the Pub/Sub push subscription to `/internal/pubsub/receipt-processing` and prints the verification token that must be stored in Secret Manager or an environment variable.

#### Teardown

When you're finished testing, run the teardown helper to remove the Cloud Run service, Cloud Function, and related IAM bindings. The script is safe to execute multiple times; it only deletes resources that still exist.

```bash
./scripts/teardown_gcp_resources.sh
```

Set `DELETE_SERVICE_ACCOUNTS=true` and/or `DELETE_ARTIFACT_REPO=true` if you also want to delete the identities or container registry created during deployment.

#### Manual deployment

Prefer running the commands yourself? Follow one of the walkthroughs:

- [Deploy the function with the gcloud CLI](docs/gcp-setup-gcloud.md#deploy-the-receipt-processing-function)
- [Deploy the function with the Cloud Console](docs/gcp-setup-cloud-console.md#deploy-the-receipt-processing-function)

Both guides explain the storage event trigger, Pub/Sub topic requirements, verification token handling, and the push subscription that targets the Cloud Run service.

#### Local smoke testing

You can exercise the publisher locally with the Functions Framework Maven plugin. Point the function at the Pub/Sub emulator or a real topic (credentials required) and send a sample Cloud Storage event:

```bash
# Start the Functions Framework on port 8081
./mvnw -pl function -am -DskipTests function:run \
    -Drun.functions.target=dev.pekelund.pklnd.function.ReceiptEventPublisher \
    -Drun.functions.port=8081

# In another terminal, send a storage event payload
curl -X POST -H "Content-Type: application/json" \
  --data-binary @docs/sample-storage-event.json \
  http://localhost:8081
```

The function logs the publish attempt and uses the configured Pub/Sub credentials. When pointed at the emulator the message appears immediately; when targeting Google Cloud you can monitor the Pub/Sub topic to confirm the published payload.

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

- `messaging` – Shared message contracts used by both the Cloud Function and the web service.
- `core` – Shared storage services and configuration reused by the web module.
- `web` – Spring MVC application with security, Firestore integration, templates, and static assets.
- `function` – Plain Java Cloud Function publisher that forwards Cloud Storage events to Pub/Sub for the web service to process.

## License

This project is provided as a starter template and may be adapted to suit your needs.
