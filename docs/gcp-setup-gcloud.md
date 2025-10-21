# Google Cloud setup with the gcloud CLI

This guide collects the command-line steps required to run ResponsiveAuthApp end to end on Google Cloud. It complements the [Cloud Console walkthrough](gcp-setup-cloud-console.md) referenced from the main [README](../README.md).

## Prerequisites

- A Google Cloud project with billing enabled and [gcloud](https://cloud.google.com/sdk/docs/install) ≥ 430.0.0 authenticated against it (`gcloud auth login`).
- Java 21 and Maven installed locally to package the receipt processor service.
- The `gcloud` CLI configured with your target project (`gcloud config set project YOUR_PROJECT_ID`).

Enable the core services once per project:

```bash
gcloud services enable \
    run.googleapis.com \
    cloudbuild.googleapis.com \
    artifactregistry.googleapis.com \
    aiplatform.googleapis.com \
    storage.googleapis.com \
    firestore.googleapis.com \
    secretmanager.googleapis.com
```

## Configure Firestore via gcloud

1. **Create (or select) your project**

    ```bash
    gcloud projects create responsive-auth-app --set-as-default
    # Or reuse an existing project
    ```

2. **Create the Firestore database**

    ```bash
    REGION=us-east1 # choose the region closest to your users (for example, us-east1 or us-central1)
    gcloud firestore databases create --location="${REGION}" --type=firestore-native
    ```

3. **Create a service account for the Spring Boot app**

    ```bash
    FIRESTORE_SA=responsive-auth-firestore@$(gcloud config get-value project).iam.gserviceaccount.com
    gcloud iam service-accounts create responsive-auth-firestore \
      --display-name="ResponsiveAuth Firestore client"

    gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
      --member="serviceAccount:${FIRESTORE_SA}" \
      --role="roles/datastore.user"

    # Optionally restrict to a custom role with only the needed Firestore permissions.
    ```

    > Cloud Run automatically uses this service account without needing a JSON key. Skip the next step unless you run the app outside Google Cloud.
    > The deployment command (or `scripts/deploy_cloud_run.sh`) attaches this account to the Cloud Run service so Google can mint short-lived tokens transparently via Application Default Credentials.

4. **(Optional) Generate a service-account key for local/off-cloud runs**

    ```bash
    mkdir -p ~/secrets
    gcloud iam service-accounts keys create ~/secrets/firestore-service-account.json \
      --iam-account="${FIRESTORE_SA}"
    ```

5. **Export the application environment variables**

    Keep any downloaded keys or OAuth credentials outside of the repository (for example under `~/.config/responsive-auth/`). Point environment variables at the files and source the helper so every shell inherits the derived values:

    ```bash
export FIRESTORE_CREDENTIALS_FILE=${FIRESTORE_CREDENTIALS_FILE:-$HOME/.config/responsive-auth/firestore.json}
export GOOGLE_OAUTH_CREDENTIALS_FILE=${GOOGLE_OAUTH_CREDENTIALS_FILE:-$HOME/.config/responsive-auth/oauth-client.json}
source ./scripts/load_local_secrets.sh

export FIRESTORE_ENABLED=true
# Leave FIRESTORE_CREDENTIALS unset on Cloud Run; ADC handles authentication automatically.
export PROJECT_ID=$(gcloud config get-value project)
export FIRESTORE_PROJECT_ID=${FIRESTORE_PROJECT_ID:-$PROJECT_ID}
export FIRESTORE_USERS_COLLECTION=users             # Optional override
export FIRESTORE_DEFAULT_ROLE=ROLE_USER             # Optional override
# Cloud Run services reuse these values so every component talks to the same database
export RECEIPT_FIRESTORE_COLLECTION=${RECEIPT_FIRESTORE_COLLECTION:-receiptExtractions}
    ```

## Configure Cloud Storage via gcloud

1. **Create the receipts bucket**

    ```bash
    BUCKET=gs://responsive-auth-receipts-$(gcloud config get-value project)
    gcloud storage buckets create "${BUCKET}" --location="${REGION}"
    # Enforce uniform bucket-level access (enabled by default for new buckets)
    gcloud storage buckets update "${BUCKET}" --uniform-bucket-level-access
    ```

2. **Create a service account dedicated to uploads**

    ```bash
    UPLOAD_SA=responsive-auth-receipts@$(gcloud config get-value project).iam.gserviceaccount.com
    gcloud iam service-accounts create responsive-auth-receipts \
      --display-name="ResponsiveAuth receipts uploader"

    gcloud storage buckets add-iam-policy-binding "${BUCKET}" \
      --member="serviceAccount:${UPLOAD_SA}" \
      --role="roles/storage.objectAdmin"
    ```

3. **Generate a key and configure the app**

    ```bash
    gcloud iam service-accounts keys create ~/secrets/gcs-receipts.json \
      --iam-account="${UPLOAD_SA}"

    export GCS_ENABLED=true
    export GCS_BUCKET=$(basename "${BUCKET}")
    export GCS_PROJECT_ID=$(gcloud config get-value project)
    export GCS_CREDENTIALS=file:/home/$USER/secrets/gcs-receipts.json  # Optional; omit on Cloud Run
    ```

## Deploy the receipt-processing Cloud Run service

### Prerequisites

Before deploying, make sure the receipt processor module builds cleanly and that your Maven configuration includes the Spring Boot plugin (the parent `pom.xml` already provides this setup):

```bash
./mvnw -pl receipt-parser -am clean package -DskipTests
```

### Provision runtime infrastructure

1. **Create the Artifact Registry repository** (skip if it already exists):

    ```bash
    REGION=us-east1  # Use the same region as your receipts bucket when possible
    PROJECT_ID=$(gcloud config get-value project)
    REPO=receipts
    gcloud artifacts repositories create "${REPO}" \n      --repository-format=docker \n      --location="${REGION}" \n      --description="Container images for the receipt processor" || true
    ```

2. **Create the receipt processor service account** and grant required roles:

    ```bash
    RECEIPT_SA=receipt-processor@${PROJECT_ID}.iam.gserviceaccount.com
    gcloud iam service-accounts create receipt-processor \n      --display-name="Receipt processor Cloud Run service" || true

    BUCKET=gs://responsive-auth-receipts-${PROJECT_ID}  # Replace if you use a custom bucket
    gcloud storage buckets add-iam-policy-binding "${BUCKET}" \n      --member="serviceAccount:${RECEIPT_SA}" \n      --role="roles/storage.objectAdmin"

    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
      --member="serviceAccount:${RECEIPT_SA}" \
      --role="roles/datastore.user"

    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
      --member="serviceAccount:${RECEIPT_SA}" \
      --role="roles/aiplatform.user"
    ```

### Build and deploy the container

1. **Build and push the image with Cloud Build**

    ```bash
    IMAGE_REPO=${REGION}-docker.pkg.dev/${PROJECT_ID}/receipts
    IMAGE_URI=${IMAGE_REPO}/pklnd-receipts:$(date +%Y%m%d-%H%M%S)

    gcloud builds submit \n      --tag "${IMAGE_URI}"
    ```

2. **Deploy the Cloud Run service**

    ```bash
    gcloud run deploy pklnd-receipts \n      --image "${IMAGE_URI}" \n      --region "${REGION}" \n      --service-account "${RECEIPT_SA}" \n      --no-allow-unauthenticated \n      --set-env-vars "SPRING_PROFILES_ACTIVE=prod,PROJECT_ID=${PROJECT_ID},VERTEX_AI_PROJECT_ID=${PROJECT_ID},VERTEX_AI_LOCATION=${REGION},VERTEX_AI_GEMINI_MODEL=gemini-2.0-flash,RECEIPT_FIRESTORE_COLLECTION=receiptExtractions" \n      --min-instances 0 \n      --max-instances 5
    ```

3. **Prune older container images** so Artifact Registry keeps only the latest build.

    ```bash
    ./scripts/cleanup_artifact_repos.sh
    ```

### Allow the web application to invoke the processor

1. **Grant the web service account invocation rights** so uploads can trigger parsing. Replace the example identity if your Cloud Run web app uses a different service account.

    ```bash
    WEB_APP_SA=responsive-auth-run-sa@${PROJECT_ID}.iam.gserviceaccount.com
    gcloud run services add-iam-policy-binding pklnd-receipts \
      --region "${REGION}" \
      --member="serviceAccount:${WEB_APP_SA}" \
      --role="roles/run.invoker"
    ```

2. **Capture the Cloud Run URL**. You'll supply this value to the web deployment so it can call the processor directly.

    ```bash
    PROCESSOR_URL=$(gcloud run services describe pklnd-receipts --region "${REGION}" --format="value(status.url)")
    echo "Set RECEIPT_PROCESSOR_BASE_URL=${PROCESSOR_URL} when deploying the web application"
    ```

3. **Deploy or update the web application** with the new environment variables. Set `RECEIPT_PROCESSOR_BASE_URL` (and optionally `RECEIPT_PROCESSOR_AUDIENCE` if you use a custom hostname) when running `scripts/deploy_cloud_run.sh` or `gcloud run deploy`. The automation script grants the default web runtime service account (`cloud-run-runtime@PROJECT_ID.iam.gserviceaccount.com`) the invoker role automatically; set `WEB_SERVICE_ACCOUNT` or `ADDITIONAL_INVOKER_SERVICE_ACCOUNTS` before re-running the receipt processor script if you need to authorize different callers.

### Verify the deployment

    ```bash
    # Upload a PDF and ensure metadata includes receipt owner information
    OBJECT=receipts/sample.pdf
    gsutil cp path/to/your/receipt.pdf "${BUCKET}/${OBJECT}"
    gsutil setmeta \
      -h "x-goog-meta-receipt.owner.id=USER_ID" \
      -h "x-goog-meta-receipt.owner.displayName=Jane Doe" \
      -h "x-goog-meta-receipt.owner.email=jane@example.com" \
      "${BUCKET}/${OBJECT}"

    # Tail recent logs from the receipt processor
    gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=pklnd-receipts" \
      --limit 20 \
      --format="table(timestamp, textPayload)"
    ```

## Troubleshooting receipt processor deployment
#### 1. Build or packaging failures
**Symptom**: Cloud Build fails to stage the application or the deployed container cannot start.

**Fix**: Build the module locally to surface compiler errors early and ensure the boot JAR is produced.

```bash
./mvnw -pl receipt-parser -am clean package -DskipTests
ls receipt-parser/target
```

If the image still fails to boot, run it locally with `spring-boot:run` to inspect stack traces before re-deploying.

#### 2. Region mismatch warnings
**Symptom**: Receipts take noticeably longer to parse after upload.

**Fix**: Keep the Cloud Run service and the storage bucket in nearby regions to reduce latency.

```bash
# Check bucket region
gcloud storage buckets describe gs://your-bucket-name --format="value(location)"
```

#### 3. Permission denied errors
**Symptom**: Cloud Run logs HTTP 403 when the web app uploads receipts.

**Fix**: Grant the web application service account `roles/run.invoker` on the processor and confirm the web deployment sets `RECEIPT_PROCESSOR_BASE_URL` (and optional `RECEIPT_PROCESSOR_AUDIENCE`).

```bash
PROJECT_ID=$(gcloud config get-value project)
WEB_APP_SA=responsive-auth-run-sa@${PROJECT_ID}.iam.gserviceaccount.com

# Allow the web app to call the receipt processor
gcloud run services add-iam-policy-binding pklnd-receipts   --region "${REGION}"   --member="serviceAccount:${WEB_APP_SA}"   --role="roles/run.invoker"
```

#### 4. API not enabled errors
**Symptom**: Deployment fails with `API [...] not enabled on project`.

**Fix**: Re-run the core `gcloud services enable` command listed earlier so Cloud Run, Artifact Registry, and Vertex AI are active.

#### 5. Environment variable issues
**Symptom**: The service deploys but fails to read Firestore or Vertex AI.

**Fix**: Confirm the deployment includes the correct variables:
- `PROJECT_ID` – project hosting the Firestore database and Vertex AI resources
- `VERTEX_AI_PROJECT_ID` – typically your current project
- `VERTEX_AI_LOCATION` – must match the chosen Vertex AI region
- `VERTEX_AI_GEMINI_MODEL` – default `gemini-2.0-flash`
- `RECEIPT_FIRESTORE_COLLECTION` – usually `receiptExtractions`

Cloud Run deployments fail fast if `PROJECT_ID` resolves to the local emulator id (for example, `pklnd-local`).
Remove any lingering `LOCAL_PROJECT_ID` exports or update `PROJECT_ID` before redeploying.

### Monitoring and Debugging

1. **View build logs** – Cloud Build prints a logs URL after each submission.
2. **Stream Cloud Run logs** – use `gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=pklnd-receipts" --limit 50`.
3. **Inspect service status** – `gcloud run services describe pklnd-receipts --region=REGION`.

The Firestore document created for the receipt mirrors the storage status fields (`RECEIVED`, `PARSING`, `COMPLETED`, `FAILED`, or `SKIPPED`) and stores Gemini's structured JSON in the `data` field. Consult the [Cloud Console setup](gcp-setup-cloud-console.md#deploy-the-receipt-processing-service) guide for screenshots and UI navigation paths.

