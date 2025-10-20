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
    eventarc.googleapis.com \
    pubsub.googleapis.com \
    aiplatform.googleapis.com \
    storage.googleapis.com \
    firestore.googleapis.com
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
export FIRESTORE_PROJECT_ID=$(gcloud config get-value project)
export FIRESTORE_USERS_COLLECTION=users             # Optional override
export FIRESTORE_DEFAULT_ROLE=ROLE_USER             # Optional override
# Cloud Run services reuse this value so every component talks to the same database
export RECEIPT_FIRESTORE_PROJECT_ID=${FIRESTORE_PROJECT_ID}
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
./mvnw -pl function -am clean package -DskipTests
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

    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \n      --member="serviceAccount:${RECEIPT_SA}" \n      --role="roles/datastore.user"

    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \n      --member="serviceAccount:${RECEIPT_SA}" \n      --role="roles/aiplatform.user"
    ```

3. **Grant the Eventarc service agent permission to impersonate the service account** (required for trigger deliveries):

    ```bash
    PROJECT_NUMBER=$(gcloud projects describe "${PROJECT_ID}" --format="value(projectNumber)")
    EVENTARC_AGENT=service-${PROJECT_NUMBER}@gcp-sa-eventarc.iam.gserviceaccount.com

    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \n      --member="serviceAccount:${EVENTARC_AGENT}" \n      --role="roles/eventarc.eventReceiver"

    gcloud iam service-accounts add-iam-policy-binding "${RECEIPT_SA}" \n      --member="serviceAccount:${EVENTARC_AGENT}" \n      --role="roles/iam.serviceAccountTokenCreator"
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
    gcloud run deploy pklnd-receipts \n      --image "${IMAGE_URI}" \n      --region "${REGION}" \n      --service-account "${RECEIPT_SA}" \n      --no-allow-unauthenticated \n      --set-env-vars "SPRING_PROFILES_ACTIVE=prod,VERTEX_AI_PROJECT_ID=${PROJECT_ID},VERTEX_AI_LOCATION=${REGION},VERTEX_AI_GEMINI_MODEL=gemini-2.0-flash,RECEIPT_FIRESTORE_PROJECT_ID=${PROJECT_ID},RECEIPT_FIRESTORE_COLLECTION=receiptExtractions" \n      --min-instances 0 \n      --max-instances 5
    ```

3. **Prune older container images** so Artifact Registry keeps only the latest build.

    ```bash
    IMAGE_RESOURCE=${IMAGE_REPO}/pklnd-receipts
    for digest in $(gcloud artifacts docker images list "${IMAGE_RESOURCE}" \
      --sort-by=~UPDATE_TIME \
      --format="get(digest)" | tail -n +2); do
      gcloud artifacts docker images delete "${IMAGE_RESOURCE}@${digest}" \
        --quiet \
        --delete-tags
    done
    ```

### Configure the Eventarc trigger

1. **Create (or update) the trigger** so Cloud Storage finalize events invoke the service. Replace the bucket name with your own if different from the earlier example.

    ```bash
    BUCKET_NAME=$(basename "${BUCKET}")
    gcloud eventarc triggers create receipt-processing-trigger \n      --location "${REGION}" \n      --destination-run-service pklnd-receipts \n      --destination-run-region "${REGION}" \n      --event-filters type=google.cloud.storage.object.v1.finalized \n      --event-filters bucket="${BUCKET_NAME}" \n      --service-account "${RECEIPT_SA}" || \
    gcloud eventarc triggers update receipt-processing-trigger \n      --location "${REGION}" \n      --destination-run-service pklnd-receipts \n      --destination-run-region "${REGION}" \n      --event-filters type=google.cloud.storage.object.v1.finalized \n      --event-filters bucket="${BUCKET_NAME}" \n      --service-account "${RECEIPT_SA}"
    ```

2. **Verify the deployment**

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
./mvnw -pl function -am clean package -DskipTests
ls function/target
```

If the image still fails to boot, run it locally with `spring-boot:run` to inspect stack traces before re-deploying.

#### 2. Region mismatch errors
**Symptom**: Eventarc reports trigger validation errors.

**Fix**: Deploy the Cloud Run service and create the trigger in the same region as your Cloud Storage bucket.

```bash
# Check bucket region
gcloud storage buckets describe gs://your-bucket-name --format="value(location)"
```

#### 3. Permission denied errors
**Symptom**: Eventarc cannot deliver events or Cloud Run returns HTTP 403.

**Fix**: Ensure both the receipt processor service account and the Eventarc service agent have the required roles.

```bash
PROJECT_ID=$(gcloud config get-value project)
PROJECT_NUMBER=$(gcloud projects describe "${PROJECT_ID}" --format="value(projectNumber)")
RECEIPT_SA=receipt-processor@${PROJECT_ID}.iam.gserviceaccount.com
EVENTARC_AGENT=service-${PROJECT_NUMBER}@gcp-sa-eventarc.iam.gserviceaccount.com

# Receipt processor roles
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${RECEIPT_SA}" \
  --role="roles/datastore.user"
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${RECEIPT_SA}" \
  --role="roles/aiplatform.user"
gcloud storage buckets add-iam-policy-binding gs://your-bucket \
  --member="serviceAccount:${RECEIPT_SA}" \
  --role="roles/storage.objectAdmin"

# Eventarc service agent permissions
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${EVENTARC_AGENT}" \
  --role="roles/eventarc.eventReceiver"
gcloud iam service-accounts add-iam-policy-binding "${RECEIPT_SA}" \
  --member="serviceAccount:${EVENTARC_AGENT}" \
  --role="roles/iam.serviceAccountTokenCreator"
```

#### 4. API not enabled errors
**Symptom**: Deployment fails with `API [...] not enabled on project`.

**Fix**: Re-run the core `gcloud services enable` command listed earlier so Cloud Run, Artifact Registry, Eventarc, and Vertex AI are active.

#### 5. Environment variable issues
**Symptom**: The service deploys but fails to read Firestore or Vertex AI.

**Fix**: Confirm the deployment includes the correct variables:
- `VERTEX_AI_PROJECT_ID` – typically your current project
- `VERTEX_AI_LOCATION` – must match the chosen Vertex AI region
- `VERTEX_AI_GEMINI_MODEL` – default `gemini-2.0-flash`
- `RECEIPT_FIRESTORE_PROJECT_ID` – project hosting the `receiptExtractions` collection
- `RECEIPT_FIRESTORE_COLLECTION` – usually `receiptExtractions`

### Monitoring and Debugging

1. **View build logs** – Cloud Build prints a logs URL after each submission.
2. **Stream Cloud Run logs** – use `gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=pklnd-receipts" --limit 50`.
3. **Inspect service status** – `gcloud run services describe pklnd-receipts --region=REGION`.

The Firestore document created for the receipt mirrors the storage status fields (`RECEIVED`, `PARSING`, `COMPLETED`, `FAILED`, or `SKIPPED`) and stores Gemini's structured JSON in the `data` field. Consult the [Cloud Console setup](gcp-setup-cloud-console.md#deploy-the-receipt-processing-service) guide for screenshots and UI navigation paths.

