# Google Cloud setup with the Cloud Console

Use this guide if you prefer configuring ResponsiveAuthApp resources through the Google Cloud Console UI. A command-line alternative is documented in the [gcloud CLI guide](gcp-setup-gcloud.md) and both are referenced from the main [README](../README.md).

### Default environment variables

- `PROJECT_ID` — shared by the web app and receipt processor.
- `VERTEX_AI_PROJECT_ID` — defaults to the receipt processor project ID.
- `VERTEX_AI_LOCATION` — defaults to `us-east1`.
- `VERTEX_AI_GEMINI_MODEL` — defaults to `gemini-2.0-flash`.
- `RECEIPT_FIRESTORE_COLLECTION` — defaults to `receiptExtractions`.
- `RECEIPT_FIRESTORE_ITEM_COLLECTION` — defaults to `receiptItems`.
- `RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION` — defaults to `receiptItemStats`.

## Firestore configuration in the Console

1. **Create (or select) a project**
   - Visit <https://console.firebase.google.com/> or <https://console.cloud.google.com/projectcreate> and either create a new project or open an existing one.
   - Note the project ID; you will reference it in environment variables and deployment commands.

2. **Provision Cloud Firestore**
   - In the Firebase console choose **Build → Firestore Database → Create database**.
   - Select the production mode ruleset, pick a region close to your users, and confirm.

3. **Create a service account**
   - Navigate to **Project settings → Service accounts** (in Firebase) or **IAM & Admin → Service Accounts** (in Google Cloud).
   - Create a new service account or reuse an existing one with Firestore access and grant it **Datastore User**.
   - When deploying to Cloud Run, you can stop here—the runtime picks up credentials automatically via the selected service account.
   - The Cloud Run deployment flow (script or console) attaches this service account to the service so Google can exchange it for short-lived tokens automatically. No JSON key is required on the server.

4. **(Optional) Generate a service account key for local/off-cloud runs**
   - If you need to run the app outside Google Cloud, click **Add key → Create new key → JSON** and download the credentials. Store them outside your source tree (for example `~/secrets/firestore-service-account.json`).

5. **Configure the Spring Boot app**
   - Keep downloaded JSON keys outside the repository (for example `~/.config/responsive-auth/firestore.json`). Point the helper at those files so the environment variables are populated without copying secrets into your shell history:

     ```bash
     export FIRESTORE_CREDENTIALS_FILE=${FIRESTORE_CREDENTIALS_FILE:-$HOME/.config/responsive-auth/firestore.json}
     export GOOGLE_OAUTH_CREDENTIALS_FILE=${GOOGLE_OAUTH_CREDENTIALS_FILE:-$HOME/.config/responsive-auth/oauth-client.json}
     source ./scripts/load_local_secrets.sh

     export FIRESTORE_ENABLED=true
     # Leave FIRESTORE_CREDENTIALS unset on Cloud Run; ADC handles authentication automatically.
     export PROJECT_ID=your-project-id                        # Shared by both Cloud Run services
     export FIRESTORE_PROJECT_ID=${FIRESTORE_PROJECT_ID:-$PROJECT_ID}
     export FIRESTORE_USERS_COLLECTION=users                  # Optional override
     export FIRESTORE_DEFAULT_ROLE=ROLE_USER                  # Optional override
     export RECEIPT_FIRESTORE_COLLECTION=${RECEIPT_FIRESTORE_COLLECTION:-receiptExtractions}
     export RECEIPT_FIRESTORE_ITEM_COLLECTION=${RECEIPT_FIRESTORE_ITEM_COLLECTION:-receiptItems}
     export RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION=${RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION:-receiptItemStats}
     ```

   - Restart the application to pick up the Firestore integration. Visit `/register` to create your first account and sign in on `/login`.

## Configure Cloud Storage in the Console

1. **Create or select the project**
   - Switch to your project at <https://console.cloud.google.com/>.

2. **Enable the Cloud Storage API**
   - Go to **APIs & Services → Library**, search for _Cloud Storage API_, and click **Enable**.

3. **Create a bucket**
   - Navigate to **Cloud Storage → Buckets → Create**.
   - Provide a globally unique name (for example `responsive-auth-receipts`).
   - Choose the region near your users and keep **Uniform bucket-level access** enabled.

4. **Create a dedicated service account**
   - Open **IAM & Admin → Service Accounts → Create service account**.
   - Name it something like `responsive-auth-receipts-uploader` and grant **Storage Object Admin**.
   - Finish the wizard without granting user access.

5. **Generate a key for uploads**
   - From the service account detail page select the **Keys** tab and click **Add key → Create new key → JSON**.
   - Store the file securely (`~/secrets/gcs-receipts.json`). Never commit credentials to version control.

6. **Configure environment variables**

   ```bash
   export GCS_ENABLED=true
   export GCS_BUCKET=responsive-auth-receipts           # Replace with your bucket name
   export GCS_CREDENTIALS=file:/home/user/secrets/gcs-receipts.json  # Optional; omit on Cloud Run
   export GCS_PROJECT_ID=your-project-id                # Optional if derived from credentials
   ```

7. **Test the receipts UI**
   - Restart the Spring Boot app and navigate to <http://localhost:8080/receipts> to upload and list files stored in the bucket.

## Deploy the receipt-processing Cloud Run service

1. **Review prerequisites**
   - Ensure the following APIs are enabled in your project: Cloud Run, Cloud Build, Artifact Registry, Vertex AI, Cloud Storage, and Firestore.
   - (Optional) Build the receipt processor module locally (`./mvnw -pl receipt-parser -am -DskipTests package`) to verify dependencies before deploying.

2. **Create or reuse a runtime service account**
   - In **IAM & Admin → Service Accounts**, create `receipt-processor` (or reuse an existing service account).
   - Grant it the following roles:
     - **Storage Object Admin** on the receipts bucket.
     - **Datastore User** for Firestore access.
     - **Vertex AI User** to invoke Gemini models.

3. **Build and publish the container image**
   - Open **Cloud Build → Builds → Create** and select **Container image**.
   - Point the source to your repository or upload the current directory (ensure it includes `project.toml`).
   - Set the destination image to `REGION-docker.pkg.dev/PROJECT_ID/receipts/pklnd-receipts`.
   - Run the build and wait for the success status.

4. **Deploy the Cloud Run service**
   - Navigate to **Cloud Run → Deploy service**.
   - Choose the image produced in step 3.
   - Set the service name to `pklnd-receipts`, pick the region that matches your bucket, and select the `receipt-processor` service account.
   - Under **Security**, keep **Allow unauthenticated invocations** disabled.
   - Expand **Variables & Secrets → Environment variables** and define:
    - `PROJECT_ID` — ensure the service uses the shared Firestore project.
    - `VERTEX_AI_PROJECT_ID` — defaults to the service project if omitted.
    - `VERTEX_AI_LOCATION` — Vertex AI region that offers Gemini (for example `us-east1`).
    - `VERTEX_AI_GEMINI_MODEL` — defaults to `gemini-2.0-flash`.
    - `RECEIPT_FIRESTORE_COLLECTION` — defaults to `receiptExtractions`.
    - `RECEIPT_FIRESTORE_ITEM_COLLECTION` — defaults to `receiptItems`.
    - `RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION` — defaults to `receiptItemStats`.
     - `RECEIPT_PROCESSOR_BASE_URL` — set to the Cloud Run URL noted after deployment.
   - Leave `RECEIPT_PROCESSOR_USE_ID_TOKEN` enabled so the web application authenticates with the processor automatically.
   - Deploy the service and note the HTTPS URL; you will reference it when configuring the web application.

5. **Allow the web application to invoke the processor**
   - In **Cloud Run → pklnd-receipts → Permissions**, add the web service account (for example `responsive-auth-run-sa@PROJECT_ID.iam.gserviceaccount.com`) with the **Cloud Run Invoker** role.
   - From the service detail page copy the service URL and configure the web application deployment with `RECEIPT_PROCESSOR_BASE_URL`.
   - If you deploy via `scripts/deploy_cloud_run.sh`, set `RECEIPT_PROCESSOR_BASE_URL` before running the script. `scripts/deploy_receipt_processor.sh` inspects the existing web service (override with `WEB_SERVICE_NAME`/`WEB_SERVICE_REGION`) and grants that runtime service account the **Cloud Run Invoker** role automatically; set `WEB_SERVICE_ACCOUNT` (or `ADDITIONAL_INVOKER_SERVICE_ACCOUNTS`) when you need to authorize different callers directly.

6. **Observe the lifecycle**
   - Upload a PDF receipt to the bucket and include optional metadata keys:
     - `receipt.owner.id`
     - `receipt.owner.displayName`
     - `receipt.owner.email`
   - Watch the logs from **Cloud Run → pklnd-receipts → Logs** or via `gcloud logging read`.
   - Inspect the Firestore collection to confirm the document status transitions (`RECEIVED`, `PARSING`, `COMPLETED`, `FAILED`, or `SKIPPED`) and review the parsed JSON stored in the `data` field.

7. **Troubleshooting**
   - Non-PDF files remain in `SKIPPED` status so you can audit unsupported uploads without invoking Gemini.
   - Errors (for example, Gemini timeouts) set the status to `FAILED` and store the message in both Firestore and the object metadata. Resolve the issue and retry by re-uploading the file.

Cross-check the [gcloud CLI instructions](gcp-setup-gcloud.md#deploy-the-receipt-processing-cloud-run-service) for equivalent commands when you need to automate the deployment.
