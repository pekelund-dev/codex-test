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
    REGION=us-central1 # choose the region closest to your users
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

4. **Generate a service-account key**

    ```bash
    mkdir -p ~/secrets
    gcloud iam service-accounts keys create ~/secrets/firestore-service-account.json \
      --iam-account="${FIRESTORE_SA}"
    ```

5. **Export the application environment variables**

    ```bash
    export FIRESTORE_ENABLED=true
    export FIRESTORE_CREDENTIALS=file:/home/$USER/secrets/firestore-service-account.json
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
    export GCS_CREDENTIALS=file:/home/$USER/secrets/gcs-receipts.json
    ```

## Deploy the receipt-processing function

1. **Package the code**

    ```bash
    ./mvnw -DskipTests package
    ```

2. **Create a runtime service account**

    ```bash
    FUNCTION_SA=receipt-parser@$(gcloud config get-value project).iam.gserviceaccount.com
    gcloud iam service-accounts create receipt-parser \
      --display-name="Receipt parsing Cloud Function"

    gcloud storage buckets add-iam-policy-binding "${BUCKET}" \
      --member="serviceAccount:${FUNCTION_SA}" \
      --role="roles/storage.objectViewer"

    gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
      --member="serviceAccount:${FUNCTION_SA}" \
      --role="roles/datastore.user"

    gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
      --member="serviceAccount:${FUNCTION_SA}" \
      --role="roles/aiplatform.user"
    ```

3. **Deploy the Cloud Function (2nd gen)**

    ```bash
    REGION=us-central1
    FUNCTION_NAME=receiptProcessingFunction
    gcloud functions deploy "${FUNCTION_NAME}" \
      --gen2 \
      --region="${REGION}" \
      --runtime=java21 \
      --entry-point=dev.pekelund.responsiveauth.function.ReceiptProcessingFunction \
      --source=. \
      --service-account="${FUNCTION_SA}" \
      --trigger-bucket=$(basename "${BUCKET}") \
      --set-env-vars=VERTEX_AI_PROJECT_ID=$(gcloud config get-value project),VERTEX_AI_LOCATION=${REGION},VERTEX_AI_GEMINI_MODEL=gemini-1.5-pro,RECEIPT_FIRESTORE_COLLECTION=receiptExtractions
    ```

4. **Verify the lifecycle**

    ```bash
    # Upload a PDF and include owner metadata
    # Ensure you have a sample PDF available (for example, download https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf
    # and rename it to receipt.pdf, or supply the path to a PDF you already have).
    OBJECT=receipts/sample.pdf
    gsutil cp path/to/your/receipt.pdf "${BUCKET}/${OBJECT}"
    gsutil setmeta \
      -h "x-goog-meta-receipt.owner.id=USER_ID" \
      -h "x-goog-meta-receipt.owner.displayName=Jane Doe" \
      -h "x-goog-meta-receipt.owner.email=jane@example.com" \
      "${BUCKET}/${OBJECT}"

    # Stream function logs
    gcloud functions logs read "${FUNCTION_NAME}" --region="${REGION}" --gen2
    ```

The Firestore document created for the receipt mirrors the storage status fields (`RECEIVED`, `PARSING`, `COMPLETED`, `FAILED`, or `SKIPPED`) and stores Gemini's structured JSON in the `data` field. Consult the [Cloud Console setup](gcp-setup-cloud-console.md#deploy-the-receipt-processing-function) guide for screenshots and UI navigation paths.
