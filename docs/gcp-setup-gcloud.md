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
# Cloud Run and the Cloud Function reuse this value so every component talks to the same database
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

## Deploy the receipt-processing function

### Prerequisites

Before deploying the Cloud Function, ensure you have the proper Maven configuration in your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
            </configuration>
        </plugin>
        <plugin>
            <groupId>com.google.cloud.functions</groupId>
            <artifactId>function-maven-plugin</artifactId>
            <version>0.10.1</version>
            <configuration>
                <functionTarget>dev.pekelund.pklnd.function.ReceiptProcessingFunction</functionTarget>
            </configuration>
        </plugin>
    </plugins>
</build>

<!-- Function module inherits the parent POM and does not need extra profiles -->
```

### Enable Required Google Cloud APIs

Before deployment, ensure all required APIs are enabled:

```bash
gcloud services enable cloudfunctions.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  run.googleapis.com \
  aiplatform.googleapis.com \
  eventarc.googleapis.com
```

1. **Package the code**

    Build only the Cloud Function module (which also compiles its shared dependencies) to verify the sources locally:

    ```bash
    ./mvnw -pl function -am clean package -DskipTests
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

    # Required for Eventarc triggers
    gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
      --member="serviceAccount:${FUNCTION_SA}" \
      --role="roles/eventarc.eventReceiver"
    ```

3. **Configure Eventarc Service Agent (if not already done)**

    ```bash
    # Get your project number
    PROJECT_NUMBER=$(gcloud projects describe $(gcloud config get-value project) --format="value(projectNumber)")
    
    # Grant required roles to Eventarc service agent
    gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
      --member="serviceAccount:service-${PROJECT_NUMBER}@gcp-sa-eventarc.iam.gserviceaccount.com" \
      --role="roles/eventarc.serviceAgent"

    gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
      --member="serviceAccount:service-${PROJECT_NUMBER}@gcp-sa-eventarc.iam.gserviceaccount.com" \
      --role="roles/run.invoker"
    ```

4. **Deploy the Cloud Function (2nd gen)**

    **Important**: The function must be deployed in the same region as your Cloud Storage bucket. Check your bucket region first:

    ```bash
    gcloud storage buckets describe "${BUCKET}" --format="value(location)"
    ```

    Then deploy with the correct region:

    ```bash
    # Use the same region as your bucket (for example, us-east1 or us-central1)
    REGION=us-east1  # Replace with your bucket's region
    FUNCTION_NAME=receiptProcessingFunction
    gcloud functions deploy "${FUNCTION_NAME}" \
      --gen2 \
      --region="${REGION}" \
      --runtime=java21 \
      --entry-point=org.springframework.cloud.function.adapter.gcp.GcfJarLauncher \
      --source=. \
      --set-build-env-vars=MAVEN_BUILD_ARGUMENTS="-pl function -am -DskipTests package" \
      --service-account="${FUNCTION_SA}" \
      --trigger-bucket=$(basename "${BUCKET}") \
      --set-env-vars=VERTEX_AI_PROJECT_ID=$(gcloud config get-value project),VERTEX_AI_LOCATION=${REGION},VERTEX_AI_GEMINI_MODEL=gemini-2.0-flash,RECEIPT_FIRESTORE_PROJECT_ID=${RECEIPT_FIRESTORE_PROJECT_ID},RECEIPT_FIRESTORE_COLLECTION=receiptExtractions
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

## Troubleshooting Cloud Function Deployment

### Understanding Code Upload and Build Process

When you run `gcloud functions deploy`, the following process occurs:

1. **Source Upload**: Your entire project directory (excluding `.gcloudignore` files) is packaged and uploaded to Google Cloud Storage
2. **Cloud Build**: Google Cloud Build downloads the source code and initiates the build process  
3. **Buildpack Processing**: The Java buildpack runs Maven in the cloud environment to compile and package your application
4. **Container Creation**: A container image is created and stored in Google Cloud's Artifact Registry
5. **Function Deployment**: The container is deployed as a Cloud Function with the specified triggers

**Important**: Your local `target/` directory is not directly used - Google Cloud rebuilds everything from source code in the cloud environment.

### Common Issues and Solutions

### Common Issues and Solutions

#### 1. "class not found" Error
**Error**: `Build failed with status: FAILURE and message: build succeeded but did not produce the class "dev.pekelund.pklnd.function.ReceiptProcessingFunction"`

**Root Cause**: Cloud Functions buildpack runs `javap -classpath target/[jar]:target/dependency/*` but finds classes in different locations than expected.

**Solutions**:

1. **Match the Cloud Build arguments**: Set `MAVEN_BUILD_ARGUMENTS="-pl function -am -DskipTests package"` when deploying (for example with `--set-build-env-vars` or via `project.toml`). This ensures the buildpack compiles the multi-module project and produces the Cloud Function JAR under `function/target`.

2. **Verify the correct structure is created locally**:
   ```bash
   ./mvnw -pl function -am clean package -DskipTests

   # Check function class is in the compiled JAR
   jar tf function/target/responsive-auth-function-0.0.1-SNAPSHOT.jar | grep ReceiptProcessingFunction

   # Check dependencies are in function/target/dependency/
   ls function/target/dependency/ | head -5

   # Test the same command the buildpack uses
   javap -classpath function/target/responsive-auth-function-0.0.1-SNAPSHOT.jar:function/target/dependency/* dev.pekelund.pklnd.function.ReceiptProcessingFunction
   ```

#### 2. Region Mismatch Error
**Error**: Function deployment fails with trigger validation errors

**Solution**: Ensure the function is deployed in the same region as your Cloud Storage bucket:
```bash
# Check bucket region
gcloud storage buckets describe gs://your-bucket-name --format="value(location)"
# Use the same region for function deployment
```

#### 3. Permission Denied Errors
**Error**: `Permission denied while using the Eventarc Service Agent` or `Permission "eventarc.events.receiveEvent" denied`

**Solution**: Grant required permissions to service accounts:
```bash
# For Eventarc Service Agent
PROJECT_NUMBER=$(gcloud projects describe $(gcloud config get-value project) --format="value(projectNumber)")
gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:service-${PROJECT_NUMBER}@gcp-sa-eventarc.iam.gserviceaccount.com" \
  --role="roles/eventarc.serviceAgent"

# For Function Service Account
gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:receipt-parser@$(gcloud config get-value project).iam.gserviceaccount.com" \
  --role="roles/eventarc.eventReceiver"
```

#### 4. API Not Enabled Errors
**Error**: `API [...] not enabled on project`

**Solution**: Enable all required APIs before deployment:
```bash
gcloud services enable cloudfunctions.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  run.googleapis.com \
  aiplatform.googleapis.com \
  eventarc.googleapis.com
```

#### 5. Environment Variable Issues
**Error**: Function runs but fails to connect to services

**Solution**: Verify environment variables are set correctly in the deployment command:
- `VERTEX_AI_PROJECT_ID`: Should match your current project
- `VERTEX_AI_LOCATION`: Should match your function deployment region
- `VERTEX_AI_GEMINI_MODEL`: Use `gemini-2.0-flash`
- `RECEIPT_FIRESTORE_PROJECT_ID`: Project that hosts the Firestore database storing receipt extractions (defaults to the function project)
- `RECEIPT_FIRESTORE_COLLECTION`: Use `receiptExtractions`

### Monitoring and Debugging

1. **View build logs**: The deployment command provides a Cloud Build logs URL
2. **Stream function logs**: Use `gcloud functions logs read FUNCTION_NAME --region=REGION --gen2`
3. **Check function status**: Use `gcloud functions describe FUNCTION_NAME --region=REGION --gen2`

The Firestore document created for the receipt mirrors the storage status fields (`RECEIVED`, `PARSING`, `COMPLETED`, `FAILED`, or `SKIPPED`) and stores Gemini's structured JSON in the `data` field. Consult the [Cloud Console setup](gcp-setup-cloud-console.md#deploy-the-receipt-processing-function) guide for screenshots and UI navigation paths.
