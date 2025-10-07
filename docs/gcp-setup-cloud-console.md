# Google Cloud setup with the Cloud Console

Use this guide if you prefer configuring ResponsiveAuthApp resources through the Google Cloud Console UI. A command-line alternative is documented in the [gcloud CLI guide](gcp-setup-gcloud.md) and both are referenced from the main [README](../README.md).

### Default environment variables

- `VERTEX_AI_PROJECT_ID` — defaults to the Cloud Function project ID.
- `VERTEX_AI_LOCATION` — defaults to `us-east1`.
- `VERTEX_AI_GEMINI_MODEL` — defaults to `gemini-2.0-flash`.
- `RECEIPT_FIRESTORE_PROJECT_ID` — defaults to the Cloud Function project ID.
- `RECEIPT_FIRESTORE_COLLECTION` — defaults to `receiptExtractions`.

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
     export FIRESTORE_PROJECT_ID=your-project-id              # Optional when derived from the key
     export FIRESTORE_USERS_COLLECTION=users                  # Optional override
     export FIRESTORE_DEFAULT_ROLE=ROLE_USER                  # Optional override
     export RECEIPT_FIRESTORE_PROJECT_ID=$FIRESTORE_PROJECT_ID # Keep Cloud Run + Cloud Function aligned
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
   export GCS_CREDENTIALS=file:/home/user/secrets/gcs-receipts.json
   export GCS_PROJECT_ID=your-project-id                # Optional if derived from credentials
   ```

7. **Test the receipts UI**
   - Restart the Spring Boot app and navigate to <http://localhost:8080/receipts> to upload and list files stored in the bucket.

## Deploy the receipt-processing function

1. **Review prerequisites**
   - Ensure the following APIs are enabled in your project: Cloud Functions, Cloud Build, Artifact Registry, Eventarc, Pub/Sub, Vertex AI, Cloud Storage, and Firestore.
   - Build the Cloud Function module locally (`./mvnw -pl function -am -DskipTests package`) to verify dependencies before deployment.

2. **Create or reuse a runtime service account**
   - In **IAM & Admin → Service Accounts**, create `receipt-parser` (or reuse an existing service account).
   - Grant it the following roles:
     - **Storage Object Viewer** on the receipts bucket (or a custom role with `storage.objects.get` and `storage.objects.update`).
     - **Datastore User** (or equivalent) for Firestore access.
     - **Vertex AI User** to invoke Gemini models.

3. **Deploy with the Cloud Console**
   - Go to **Cloud Functions → Create Function** and choose **2nd gen**.
   - Specify:
     - **Name**: `receiptProcessingFunction` (or another identifier used by your triggers).
     - **Region**: the same region as your bucket and Firestore database.
     - **Trigger**: **Cloud Storage** with the **Finalized/Created** event and select your receipts bucket.
     - **Runtime**: Java 21.
     - **Entry point**: `org.springframework.cloud.function.adapter.gcp.GcfJarLauncher`.
     - **Service account**: the `receipt-parser` account created earlier.
   - Expand **Runtime, build, connections and security settings → Environment variables** and define:
     - `VERTEX_AI_PROJECT_ID` — defaults to the function project if omitted.
     - `VERTEX_AI_LOCATION` — Vertex AI region that offers Gemini (for example `us-east1` or `us-central1`).
     - `VERTEX_AI_GEMINI_MODEL` — defaults to `gemini-2.0-flash`.
     - `RECEIPT_FIRESTORE_PROJECT_ID` — reuse the same project ID exported for the web app to keep all components on one database.
     - `RECEIPT_FIRESTORE_COLLECTION` — defaults to `receiptExtractions`.
   - Upload the source from your local machine or connect the repository, then click **Deploy**.

4. **Observe the lifecycle**
   - Upload a PDF receipt to the bucket and include optional metadata keys:
     - `receipt.owner.id`
     - `receipt.owner.displayName`
     - `receipt.owner.email`
   - Watch the function logs from **Cloud Functions → Logs** or via `gcloud functions logs read`.
   - Inspect the Firestore collection to confirm the document status transitions (`RECEIVED`, `PARSING`, `COMPLETED`, `FAILED`, or `SKIPPED`) and review the parsed JSON stored in the `data` field.

5. **Troubleshooting**
   - Non-PDF files remain in `SKIPPED` status so you can audit unsupported uploads without invoking Gemini.
   - Errors (for example, Gemini timeouts) set the status to `FAILED` and store the message in both Firestore and the object metadata. Resolve the issue and retry by re-uploading the file.

Cross-check the [gcloud CLI instructions](gcp-setup-gcloud.md#deploy-the-receipt-processing-function) for equivalent commands when you need to automate the deployment.
