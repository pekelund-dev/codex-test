# Local development without Google Cloud

Running the entire stack locally avoids container builds in Artifact Registry and keeps
experimentation free. This guide explains how to launch the Firestore emulator, configure
matching environment variables, and run the Spring Boot web app plus the receipt parsing
function on your workstation.

> ℹ️ The JSON key files you use locally are **not** required when the container runs on Cloud Run. Google injects the attached service account automatically in managed environments, so keep the downloaded keys on your workstation only.

## Prerequisites

- **Java 21** and **Maven 3.9+** for building the modules.
- The **Google Cloud CLI (`gcloud`)** with the beta components installed. The Firestore
  emulator ships with `gcloud`.
- Bash 4 or newer. All helper scripts live under `scripts/`.

Optional but recommended:

- [`jq`](https://stedolan.github.io/jq/) for pretty-printing JSON responses when you test
  the local receipt parser.

## 1. Start the Firestore emulator

The Firestore emulator keeps user registration data and receipt parsing output during local
development. Start it in its own terminal so you can leave it running while you code:

```bash
./scripts/start_firestore_emulator.sh
```

Key characteristics:

- The emulator listens on `localhost:8085` and persists data in `.local/firestore` by default.
  Override these values with `FIRESTORE_EMULATOR_HOST` and `FIRESTORE_EMULATOR_DATA_DIR` if
  you need different ports or storage paths.
- The script is idempotent. If another emulator instance is already bound to the host/port,
  it simply prints a warning and exits without terminating the existing process.
- Use `Ctrl+C` in the emulator terminal when you want to stop it. The on-disk data directory
  survives so you can resume where you left off.

## 2. Source the local environment helper

The application expects several environment variables to point at the shared Firestore
project. Rather than exporting them manually each session, source the helper script in every
terminal where you plan to run Maven commands:

```bash
source ./scripts/source_local_env.sh
```

The script performs the following configuration:

- Sets `FIRESTORE_ENABLED=true` so the web app uses Firestore instead of the in-memory fallback.
- Points `FIRESTORE_PROJECT_ID`, `RECEIPT_FIRESTORE_PROJECT_ID`, and `GOOGLE_CLOUD_PROJECT`
  to the local logical project (`pklnd-local` by default).
- Publishes `FIRESTORE_EMULATOR_HOST=localhost:8085` and clears credential variables so the
  Firestore SDK automatically targets the emulator with anonymous authentication.
- Disables Google Cloud Storage integration (`GCS_ENABLED=false`) because the emulator flow
  does not require Cloud Storage.

Feel free to override the defaults before sourcing the script:

```bash
export LOCAL_PROJECT_ID=my-local-project
export FIRESTORE_EMULATOR_HOST=127.0.0.1:9000
source ./scripts/source_local_env.sh
```

When you need to target your actual Firestore project or reuse Google OAuth credentials locally, keep the downloaded JSON files outside the repository and set `FIRESTORE_CREDENTIALS_FILE` / `GOOGLE_OAUTH_CREDENTIALS_FILE` before sourcing the helper:

```bash
export FIRESTORE_CREDENTIALS_FILE=$HOME/.config/responsive-auth/firestore.json
export GOOGLE_OAUTH_CREDENTIALS_FILE=$HOME/.config/responsive-auth/oauth-client.json
source ./scripts/load_local_secrets.sh
```

This leaves the emulator configuration intact but also makes the credentials available if you temporarily disable the emulator host variables to run against managed Firestore.

## 3. Run the web application

With the emulator running and the environment sourced, start the web module in development
mode:

```bash
./mvnw -Pinclude-web -pl web -am spring-boot:run
```

Navigate to <http://localhost:8080> and exercise the registration flow. User accounts are
stored in the Firestore emulator and persist across restarts as long as you keep the emulator
`DATA_DIR` around.

To pre-create admin or demo accounts without hitting Firestore you can still configure
fallback users through `application.yml`. When Firestore is enabled those fallback users are
ignored, so the emulator behaves just like the managed service.

## 4. Exercise the receipt processing service locally

Two options exist depending on how closely you need to mirror production:

### Option A: Full Cloud Run pipeline

When you want to execute the same code that runs on Cloud Run—including Vertex AI requests—start the service locally with Spring Boot. Make sure the Firestore emulator is running and source the local environment helper in the same shell, then add the extra environment variables that Vertex AI requires:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/a/service-account.json
export VERTEX_AI_PROJECT_ID=my-vertex-project
export VERTEX_AI_LOCATION=us-east1
./mvnw -pl receipt-parser -am spring-boot:run
```

Send a Cloud Storage finalize event payload to <http://localhost:8080/events/storage>. The Firestore emulator receives all writes automatically because the `FIRESTORE_EMULATOR_HOST` environment variable is already exported.

### Option B: Local receipt test profile (emulator only)

If you only need to evaluate the legacy PDF extractor and Firestore writes—without incurring Vertex AI costs—start the lightweight Spring profile:

```bash
./mvnw -pl receipt-parser -am spring-boot:run \
    -Dspring-boot.run.profiles=local-receipt-test
```

Submit a PDF for parsing:

```bash
curl -F "file=@test-receipt.pdf" http://localhost:8080/local-receipts/parse | jq
```

The handler writes the structured output to the Firestore emulator so you can review it in
another terminal or export it later.

## 5. Inspecting emulator data

Use the Firestore REST API to inspect collections during development. For example, list the
registered users:

```bash
curl "http://${FIRESTORE_EMULATOR_HOST}/v1/projects/${FIRESTORE_PROJECT_ID}/databases/(default)/documents/${FIRESTORE_USERS_COLLECTION}"
```

Or query receipt extraction results:

```bash
curl "http://${FIRESTORE_EMULATOR_HOST}/v1/projects/${RECEIPT_FIRESTORE_PROJECT_ID}/databases/(default)/documents/${RECEIPT_FIRESTORE_COLLECTION}"
```

Because the emulator does not enforce authentication, these endpoints are reachable without
OAuth tokens.

## 6. Cleaning up

- Stop the emulator with `Ctrl+C`.
- Delete `.local/firestore` if you want to remove all local state.
- Run `unset $(grep -o '^[A-Z0-9_]*' scripts/source_local_env.sh | tr '\n' ' ')` to clear the
  exported variables, or simply close the terminal where you sourced the script.

With these steps you can iterate on the full stack locally, then switch back to the managed
GCP deployment when you are ready to validate infrastructure or latency behavior.
