# Google Cloud setup with the gcloud CLI

This guide collects the command-line steps required to run ResponsiveAuthApp end to end on Google Cloud. It complements the [Cloud Console walkthrough](gcp-setup-cloud-console.md) referenced from the main [README](../README.md).

## Prerequisites

- A Google Cloud project with billing enabled and [gcloud](https://cloud.google.com/sdk/docs/install) ≥ 430.0.0 authenticated against it (`gcloud auth login`).
- Java 21 and Maven installed locally to package the Cloud Function artifact.
- The `gcloud` CLI configured with your target project (`gcloud config set project YOUR_PROJECT_ID`).

Enable the core services once per project:

```bash
gcloud services enable \
    cloudfunctions.googleapis.com \
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

## Deploy the receipt-processing function

The new Cloud Function simply publishes a Pub/Sub message whenever a receipt lands in
the bucket. The heavy parsing work now runs inside the Cloud Run service.

### Enable required APIs

```bash
gcloud services enable 
  cloudfunctions.googleapis.com 
  cloudbuild.googleapis.com 
  artifactregistry.googleapis.com 
  eventarc.googleapis.com 
  pubsub.googleapis.com 
  run.googleapis.com 
  storage.googleapis.com
```

### Build the function module

```bash
./mvnw -pl function -am clean package -DskipTests
```

> The deployment commands below pass `--set-build-env-vars=^:^BP_MAVEN_BUILD_ARGUMENTS=-pl function -am -DskipTests clean package`
> so Cloud Build executes the same multi-module command during `gcloud functions deploy`.
> The `^:^` prefix switches the separator character, which lets us keep the spaces inside the Maven argument string.
> Keep that override in sync with the local build steps to ensure the messaging jar is
> available when the Functions Framework stages dependencies.

### Create a minimal service account

```bash
FUNCTION_SA=receipt-parser@$(gcloud config get-value project).iam.gserviceaccount.com
gcloud iam service-accounts create receipt-parser 
  --display-name="Receipt event publisher"

gcloud storage buckets add-iam-policy-binding "${BUCKET}" 
  --member="serviceAccount:${FUNCTION_SA}" 
  --role="roles/storage.objectViewer"

gcloud projects add-iam-policy-binding $(gcloud config get-value project) 
  --member="serviceAccount:${FUNCTION_SA}" 
  --role="roles/pubsub.publisher"
```

### Create (or reuse) the Pub/Sub topic

```bash
RECEIPT_PUBSUB_TOPIC=${RECEIPT_PUBSUB_TOPIC:-receipt-processing}
RECEIPT_PUBSUB_PROJECT_ID=${RECEIPT_PUBSUB_PROJECT_ID:-$(gcloud config get-value project)}
gcloud pubsub topics create projects/${RECEIPT_PUBSUB_PROJECT_ID}/topics/${RECEIPT_PUBSUB_TOPIC} || true
```

### Deploy the 2nd gen function

```bash
REGION=$(gcloud storage buckets describe "${BUCKET}" --format="value(location)" | tr '[:upper:]' '[:lower:]')
FUNCTION_NAME=receiptProcessingFunction

gcloud functions deploy "${FUNCTION_NAME}" 
  --gen2 
  --runtime=java21 
  --region="${REGION}" 
  --source=. 
  --entry-point=dev.pekelund.pklnd.function.ReceiptEventPublisher
  --service-account="${FUNCTION_SA}"
  --trigger-bucket=$(basename "${BUCKET}")
  --set-env-vars="RECEIPT_PUBSUB_TOPIC=${RECEIPT_PUBSUB_TOPIC},RECEIPT_PUBSUB_PROJECT_ID=${RECEIPT_PUBSUB_PROJECT_ID}"
  --set-build-env-vars=^:^BP_MAVEN_BUILD_ARGUMENTS=-pl function -am -DskipTests clean package
  --memory=512Mi
  --timeout=120s
```

### Verify the publish pipeline

```bash
# Upload a PDF to trigger the function
OBJECT=receipts/sample.pdf
gsutil cp path/to/your/receipt.pdf "${BUCKET}/${OBJECT}"

# Tail function logs to confirm the publish succeeded
gcloud functions logs read "${FUNCTION_NAME}" --region="${REGION}" --gen2
```

After the Cloud Run deployment is complete, create a push subscription that points to
`https://SERVICE_URL/internal/pubsub/receipt-processing` and supply the same token stored
in `RECEIPT_PROCESSING_PUBSUB_VERIFICATION_TOKEN`. The Cloud Run deployment guide includes
the exact `gcloud pubsub subscriptions` commands.

### Monitoring and Debugging

1. **View build logs**: The deployment command provides a Cloud Build logs URL
2. **Stream function logs**: Use `gcloud functions logs read FUNCTION_NAME --region=REGION --gen2`
3. **Check function status**: Use `gcloud functions describe FUNCTION_NAME --region=REGION --gen2`

When the Cloud Run service finishes parsing a receipt it mirrors the storage status fields (`RECEIVED`, `PARSING`, `COMPLETED`, `FAILED`, or `SKIPPED`) in Firestore and stores Gemini's structured JSON in the `data` field. Consult the [Cloud Console setup](gcp-setup-cloud-console.md#deploy-the-receipt-processing-function) guide for screenshots and UI navigation paths.
