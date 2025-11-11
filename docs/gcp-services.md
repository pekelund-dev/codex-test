# Google Cloud Services Checklist

Use this reference to review every Google Cloud Platform (GCP) service that the
pklnd deployments rely on. Each section explains the purpose of the service,
highlights whether staging and production share the same instance, and provides
quick setup steps using both the Cloud Console and the `gcloud` CLI where
relevant.

> ✅ **Tip:** Provision these resources once per shared infrastructure project
> (for example `pklnd-shared-infra`) and point both staging and production
> deployments at them via the environment variables listed in
> [environment-variables.md](./environment-variables.md). Cloud Run services can
> live in separate projects while still consuming the shared Firestore database
> and Cloud Storage bucket.

---

## Cloud Run

**Purpose:** Hosts the web frontend (`pklnd-web`/`pklnd-web-test`) and receipt
processor (`pklnd-receipts`/`pklnd-receipts-test`) containers. Provides HTTPS
endpoints with automatic scaling and integrates with IAM for authenticated
invocations.

**Setup steps:**

1. Enable the API: `gcloud services enable run.googleapis.com`.
2. Create or reuse an Artifact Registry repository for container images
   (see below).
3. Deploy with the automation scripts (`scripts/deploy_cloud_run.sh` or
   `scripts/deploy_receipt_processor.sh`) or via the console:
   - Console: **Cloud Run → Create service** → select region → supply container
     image → attach the runtime service account.
   - CLI example for the web service:

     ```bash
     gcloud run deploy pklnd-web \
       --image "${IMAGE_URI}" \
       --region "${REGION}" \
       --service-account "${SA_EMAIL}" \
       --allow-unauthenticated
     ```

---

## Cloud Build

**Purpose:** Builds container images for both services directly from the Git
repository. Required when invoking the deployment scripts so Google Cloud can
compile Docker images on demand.

**Setup steps:**

1. Enable the API: `gcloud services enable cloudbuild.googleapis.com`.
2. Grant the Cloud Build service account (`PROJECT_NUMBER@cloudbuild.gserviceaccount.com`)
   permission to push to Artifact Registry (`roles/artifactregistry.writer`).
3. Run a build to verify configuration:

   ```bash
   gcloud builds submit "${BUILD_CONTEXT:-.}" --tag "${IMAGE_URI}"
   ```

---

## Artifact Registry

**Purpose:** Stores versioned container images for the web and receipt processor
services. The deployment scripts publish images to repositories named `web` and
`receipts` respectively.

**Setup steps:**

1. Enable the API: `gcloud services enable artifactregistry.googleapis.com`.
2. Create Docker repositories in the shared region (example for the web service):

   ```bash
   gcloud artifacts repositories create web \
     --repository-format=docker \
     --location="${REGION}" \
     --description="Container images for the pklnd web service"
   ```

3. Grant the Cloud Run runtime service accounts `roles/artifactregistry.reader`.
4. Update DNS or routing only after the container is deployed via Cloud Run.

---

## Firestore (Native mode)

**Purpose:** Stores user profiles, authentication metadata, and receipt parsing
results shared by both Cloud Run services. Staging and production reuse the same
Firestore database by default to keep data consistent.

**Setup steps:**

1. Enable the API: `gcloud services enable firestore.googleapis.com`.
2. Console: **Firestore → Create database** → select **Native mode** → choose a
   region (match your Cloud Run region when possible).
3. CLI:

   ```bash
   gcloud firestore databases create \
     --region="${REGION}" \
     --type=firestore-native
   ```

4. Grant runtime service accounts the `roles/datastore.user` role.

---

## Cloud Storage

**Purpose:** Stores uploaded receipt files for both environments. The
`GCS_BUCKET` value usually points to a shared bucket so staging and production
operate on the same dataset.

**Setup steps:**

1. Enable the API: `gcloud services enable storage.googleapis.com`.
2. Console: **Cloud Storage → Create bucket** → use a globally unique name → set
   location type to **Region** with the same location as Cloud Run.
3. CLI:

   ```bash
   gsutil mb -l "${REGION}" "gs://${GCS_BUCKET}"
   ```

4. Grant the web and receipt processor service accounts the
   `roles/storage.objectAdmin` role on the bucket.
5. Remove legacy Cloud Storage notifications so only authenticated Cloud Run
   invocations trigger the receipt processor.

---

## IAM Service Accounts & IAM Roles

**Purpose:** Secure access to shared infrastructure. Each Cloud Run service
runs as its own service account so permissions can be scoped precisely.

**Setup steps:**

1. Create service accounts for the web and receipt processor deployments:

   ```bash
   gcloud iam service-accounts create cloud-run-runtime \
     --display-name "Cloud Run Runtime"

   gcloud iam service-accounts create receipt-processor \
     --display-name "Receipt Processor Runtime"
   ```

2. Grant required roles:
   - `roles/run.invoker` (if you need authenticated invocations between
     services).
   - `roles/datastore.user` (Firestore access).
   - `roles/storage.objectAdmin` (receipt bucket access).
   - `roles/logging.logWriter` (structured logs).
   - `roles/aiplatform.user` (Vertex AI for the receipt processor).
3. Attach the relevant service account during Cloud Run deployment.

---

## Vertex AI (Gemini)

**Purpose:** Powers receipt text extraction through the Gemini model. Only the
receipt processor Cloud Run service requires this integration.

**Setup steps:**

1. Enable the API: `gcloud services enable aiplatform.googleapis.com`.
2. Ensure the runtime service account has `roles/aiplatform.user` in the Vertex
   AI project (defaults to the deployment project unless overridden).
3. (Optional) Create a separate project for Vertex AI usage and set
   `VERTEX_AI_PROJECT_ID` / `VERTEX_AI_LOCATION` in the deployment script to
   point at it.
4. Verify access with a simple test call:

   ```bash
   gcloud ai endpoints list --project "${VERTEX_AI_PROJECT_ID}" --location "${VERTEX_AI_LOCATION}"
   ```

---

## Cloud Logging

**Purpose:** Captures application logs emitted by both Cloud Run services. Logs
are visible in the Google Cloud console and can export to sinks if desired.

**Setup steps:**

1. Enabled automatically with most projects; verify via `gcloud services list`.
2. Grant runtime service accounts `roles/logging.logWriter`.
3. Review logs under **Logging → Logs Explorer** after the first deployment.
4. Configure log-based metrics or export sinks as needed for monitoring.

---

## Secret Manager (optional)

**Purpose:** Stores OAuth credentials or other sensitive configuration without
checking files into source control. The deployment scripts can run without
Secret Manager, but using it centralises secret rotation.

**Setup steps:**

1. Enable the API: `gcloud services enable secretmanager.googleapis.com`.
2. Create secrets for OAuth credentials or API keys:

   ```bash
   gcloud secrets create oauth-client-secret \
     --replication-policy="automatic"

   echo -n "${GOOGLE_CLIENT_SECRET}" | gcloud secrets versions add oauth-client-secret --data-file=-
   ```

3. Grant runtime service accounts `roles/secretmanager.secretAccessor` if the
   application reads values at runtime.
4. Reference secrets from the deployment scripts or populate environment
   variables with `gcloud secrets versions access` during setup.

---

## Cloud DNS / Domain Mapping

**Purpose:** Routes public traffic to the Cloud Run services using the custom
domains `pklnd.pekelund.dev` and `test.pkelund.dev`.

**Setup steps:**

1. Configure domain mappings in Cloud Run after deploying each service:

   ```bash
   gcloud run domain-mappings create \
     --service="pklnd-web" \
     --domain="pklnd.pekelund.dev" \
     --region="${REGION}"
   ```

2. Add the provided TXT and CNAME/ALIAS records to your DNS provider (Porkbun).
3. Repeat for the staging domain (`pklnd-web-test` → `test.pkelund.dev`).
4. Verify SSL certificate provisioning before rolling out to users.

---

## Verification Checklist

- [ ] Required APIs enabled (`run`, `cloudbuild`, `artifactregistry`,
      `firestore`, `storage`, `aiplatform`, `logging`, optional `secretmanager`).
- [ ] Firestore database created in Native mode.
- [ ] Cloud Storage bucket exists with correct IAM bindings.
- [ ] Artifact Registry repositories created for `web` and `receipts` images.
- [ ] Cloud Run services deployed for web and receipt processor (prod + staging).
- [ ] Runtime service accounts granted Firestore, Storage, Vertex AI, and
      Logging permissions.
- [ ] Domain mappings created for production and staging URLs.

Keep this checklist handy when onboarding teammates or preparing a new Google
Cloud project so nothing is missed before running the deployment scripts.
