# Deploying the Web Application to Google Cloud Run

This guide walks through provisioning Google Cloud resources, deploying the application to Cloud Run, configuring DNS, and verifying connectivity with Firestore and other Google Cloud services. It contains both **Google Cloud Console** and **gcloud CLI** workflows. Use whichever best fits your workflow, but keep the variables consistent across both approaches.

> 📈 Need a visual reference before diving into the steps? Review the [System Architecture Schematics](./system-architecture-diagrams.md) for high-level diagrams that outline service relationships, data flows, and deployment pipelines.

---

## Prerequisites

1. **Google Cloud project** with billing enabled.
2. **gcloud CLI** installed locally and authenticated (`gcloud init`).
3. Access to the Porkbun DNS dashboard for the `pklnd.pekelund.dev` subdomain.
4. Container image registry access (Artifact Registry or Docker Hub) for storing the built application image.
5. Provisioned Google Cloud services as outlined in [gcp-services.md](./gcp-services.md) so Firestore, Cloud Storage, and supporting APIs are ready before deployment.
6. Source repository cloned locally with access to the application code.

---

## Resource Naming

Replace the placeholders below with your values when following the steps:

| Placeholder | Description |
|-------------|-------------|
| `PROJECT_ID` | Google Cloud project ID (e.g., `my-project`). |
| `REGION` | Supported Cloud Run region (e.g., `europe-north1`). |
| `SERVICE_NAME` | Desired Cloud Run service name (e.g., `pklnd-web`). |
| `IMAGE_URI` | Fully qualified container image reference (e.g., `europe-north1-docker.pkg.dev/PROJECT_ID/web/app:latest`). |
| `DOMAIN` | Fully qualified domain to map (e.g., `pklnd.pekelund.dev`). |
| `SA_NAME` | Service account name (e.g., `cloud-run-runtime`). |
| `SHARED_FIRESTORE_PROJECT_ID` | Project that hosts the single Firestore database used by Cloud Run services and user registration. Defaults to `PROJECT_ID`. |

---

## Automation Scripts and Idempotency Guarantees

Prefer the repository scripts when you want a repeatable, idempotent rollout:

- `scripts/deploy_cloud_run.sh` provisions APIs, Artifact Registry, Firestore, the runtime service account, and the Cloud Run service. It skips resource creation when assets already exist and expects the repository `Dockerfile` at the project root (set `BUILD_CONTEXT` if you store it elsewhere). Pass `--env staging` or invoke the convenience wrapper `scripts/deploy_cloud_run_test.sh` to target the staging service with its own service/domain defaults while continuing to reuse the shared Firestore and Cloud Storage resources. Prefix overrides with `PROD_` or `STAGING_` (for example `STAGING_PROJECT_ID`, `PROD_GCS_BUCKET`) to switch between environments without exporting a new base variable each time.
- `scripts/deploy_receipt_processor.sh` provisions the receipt processor Cloud Run service, grants it access to the receipt bucket, Firestore, Vertex AI, and Cloud Logging, and aligns IAM permissions with the shared Firestore project. Existing buckets, databases, and bindings are detected so the script can be executed multiple times safely. The script now inspects the deployed web service (defaults: `WEB_SERVICE_NAME=pklnd-web`, `WEB_SERVICE_REGION=REGION`) and automatically grants its runtime service account the `roles/run.invoker` permission. Override `WEB_SERVICE_ACCOUNT` when you want to target a specific identity or use `ADDITIONAL_INVOKER_SERVICE_ACCOUNTS` for extra callers. It also removes any legacy Cloud Storage notifications on the receipt bucket so only the web application’s authenticated callbacks reach the processor. Container builds use the repository root as the build context while compiling the image from `receipt-parser/Dockerfile` via `receipt-parser/cloudbuild.yaml`; set `RECEIPT_DOCKERFILE`, `RECEIPT_BUILD_CONTEXT`, or `RECEIPT_CLOUD_BUILD_CONFIG` before running the script if you maintain a different layout, keeping the Dockerfile within the chosen context. Use `scripts/deploy_receipt_processor_test.sh` for staging deployments or the same `PROD_`/`STAGING_` prefixes when running the main script.
- `scripts/cleanup_artifact_repos.sh` prunes older container images from both Artifact Registry repositories, keeping only the newest build for each Cloud Run service.
- `scripts/teardown_gcp_resources.sh` removes both Cloud Run services, IAM bindings, and optional supporting infrastructure. It tolerates partially deleted projects and only removes what is present. Set `DELETE_SERVICE_ACCOUNTS=true` and/or `DELETE_ARTIFACT_REPO=true` when you also want to purge the associated identities or container registry.

### First-time preparation before running deployment scripts

The full checklist also lives in [first-time-deployment-checklist.md](./first-time-deployment-checklist.md) for quick sharing. Complete these tasks once before executing any of the four deployment helpers (`deploy_cloud_run.sh`, `deploy_cloud_run_test.sh`, `deploy_receipt_processor.sh`, `deploy_receipt_processor_test.sh`):

1. **Authenticate and set up the Google Cloud SDK.** Install the Google Cloud SDK, run `gcloud auth login` followed by `gcloud auth application-default login`, and ensure your account has permissions to enable APIs, manage IAM, and deploy to Cloud Run in both the staging and production projects.
2. **Load environment profiles.** Export the `PROD_` and `STAGING_` variables documented in [environment-variables.md](./environment-variables.md) so each script can select the right project, region, and shared resources automatically. Include any `SHARED_INFRA_PROJECT_ID` overrides if Firestore or Cloud Storage live in a separate project.
3. **Provision shared services.** Follow [gcp-services.md](./gcp-services.md) to create the shared Firestore database, receipt bucket, Artifact Registry repositories, and service accounts. These steps only need to be completed once; the automation detects and reuses existing resources on subsequent deployments.
4. **Confirm executable permissions.** Ensure the repository scripts are executable (`chmod +x scripts/*.sh`) if your checkout stripped the execute bit so the helpers run without manual prompts.

After completing this checklist you can run the staging wrappers to validate configuration, then promote the validated build to production using the corresponding scripts.

The rest of this document mirrors what the scripts perform under the hood if you prefer to click through the console or run individual `gcloud` commands.

---

## Environment-aware deployments and promotion workflow

`deploy_cloud_run.sh` now understands dedicated staging and production deployments. The script derives sane defaults for each environment so you only need to override the project and shared resource identifiers. Refer to [environment-variables.md](./environment-variables.md) for ready-to-copy snippets that separate `PROD_` and `STAGING_` exports.

| Environment flag | Default service name | Default domain | Notes |
|------------------|----------------------|----------------|-------|
| `--env prod` (default) | `pklnd-web` | `pklnd.pekelund.dev` | Public production service. |
| `--env staging` | `pklnd-web-test` | `test.pkelund.dev` | Private or limited-access staging instance for smoke testing. |

Both environments default to the same Firestore and Cloud Storage project so receipts, authentication data, and uploaded files remain in one shared dataset. Override `SHARED_INFRA_PROJECT_ID`, `SHARED_FIRESTORE_PROJECT_ID`, or `SHARED_GCS_PROJECT_ID` only when the database and bucket live in another project. Prefix overrides with `PROD_` or `STAGING_` (for example `STAGING_PROJECT_ID`, `PROD_GCS_BUCKET`) to keep environment-specific values side by side, and rely on the wrapper `scripts/deploy_cloud_run_test.sh` when you want a staging deployment without explicitly passing `--env`.

### Deploy the staging build

```bash
STAGING_PROJECT_ID="pklnd-test-project"
STAGING_REGION="europe-north1"
STAGING_GCS_BUCKET="pklnd-receipts"
STAGING_GOOGLE_CLIENT_ID="..."
STAGING_GOOGLE_CLIENT_SECRET="..."

./scripts/deploy_cloud_run_test.sh
```

The command provisions (or reuses) the staging Cloud Run service named `pklnd-web-test` and maps `test.pkelund.dev` by default. Inspect the newly created revision, verify authentication, and walk through receipt uploads before promoting the release.

### Promote the verified revision to production

1. Capture the staged image reference so production reuses the same container build:

   ```bash
   STAGING_IMAGE_URI=$(gcloud run services describe pklnd-web-test \
     --region "$REGION" \
     --format 'value(spec.template.spec.containers[0].image)')
   ```

2. Deploy the production service with the captured image:

```bash
PROD_PROJECT_ID="pklnd-prod-project"
PROD_REGION="europe-north1"
PROD_GCS_BUCKET="pklnd-receipts"

IMAGE_URI="$STAGING_IMAGE_URI" ./scripts/deploy_cloud_run.sh --env prod
```

   Providing `IMAGE_URI` instructs the script to skip Cloud Build and reuse the staged artifact. This guarantees the production rollout matches what you tested in staging.

3. After verifying the production deployment, clean up old revisions if desired with `gcloud run services update-traffic --splits REVISION=100 --region "$REGION" --platform managed` or prune unused images via `scripts/cleanup_artifact_repos.sh`.

> **Tip:** When staging and production share the same Firestore and Cloud Storage projects, you do not need to re-run database migrations or bucket configuration. The IAM bindings and environment variables applied by the script keep both services connected to the shared infrastructure automatically.

---

## 1. Enable Required APIs

### Console

1. Open **Cloud Console** → **APIs & Services** → **Library**.
2. Enable the following APIs if they are not already enabled:
   - Cloud Run Admin API
   - Cloud Build API
   - Artifact Registry API (or Container Registry if you still use it)
   - Firestore API
   - Cloud Storage API (required for receipt uploads)
   - Secret Manager API (if the app reads secrets)

### CLI

```bash
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  firestore.googleapis.com \
  secretmanager.googleapis.com
```

---

## 2. Configure Firestore and Supporting Infrastructure

### Console

1. Go to **Firestore** → **Create database**.
2. Select **Native mode** (required for Cloud Run integrations) and choose the same `REGION` that you plan to use for Cloud Run when possible.
3. Complete the wizard.
4. If the application relies on Firebase authentication or other services, configure them now.

### CLI

Firestore database creation is a one-time operation. If not already created, run:

```bash
gcloud firestore databases create --region=REGION --type=firestore-native
```

> **Note:** If a Firestore database already exists in the project, this command will fail. That is expected—Firestore supports only one database per project.

### Share the database across all components

The same Firestore database stores both the **user registration** data managed by the Cloud Run web application and the **receipt extraction** documents persisted by the receipt processor service. To keep data consistent:

1. Use the same `PROJECT_ID` (or explicitly set `SHARED_FIRESTORE_PROJECT_ID`) for every deployment script and console workflow.
2. Keep the `users` collection for authentication data alongside the `receiptExtractions`, `receiptItems`, and `receiptItemStats` collections managed by the receipt processor so they stay in the same database.
3. Reuse the runtime service accounts created in this guide (or grant them `roles/datastore.user`) so both Cloud Run services and any local admin scripts can all read/write the shared documents.
4. When setting environment variables, ensure both services share the same `PROJECT_ID`, `RECEIPT_FIRESTORE_COLLECTION`, `RECEIPT_FIRESTORE_ITEM_COLLECTION`, and `RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION`. If you override collection names, update both components accordingly.

> 💡 **No service-account keys needed on Cloud Run:** the deployed service automatically authenticates with Firestore through its runtime service account. Leave `FIRESTORE_CREDENTIALS` unset when running on Cloud Run or other Google Cloud hosts that support [Application Default Credentials](https://cloud.google.com/docs/authentication/provide-credentials-adc). Only create JSON keys for local development or third-party platforms that cannot use Workload Identity.

If you already downloaded key files for other environments, set `FIRESTORE_CREDENTIALS_FILE` and/or `GOOGLE_OAUTH_CREDENTIALS_FILE` to the secure file locations (outside the repository) and `source ./scripts/load_local_secrets.sh` before running any commands. The helper turns those paths into the runtime variables (`FIRESTORE_CREDENTIALS`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) so both the CLI snippets below and the automation scripts reuse your stored secrets without editing them manually.

---

## 3. Create a Runtime Service Account and Grant Permissions

### Console

1. Navigate to **IAM & Admin** → **Service Accounts** → **Create service account**.
2. Provide `SA_NAME` and description, then click **Create and continue**.
3. Grant the service account the following roles:
   - **Cloud Run Service Agent** (`roles/run.serviceAgent`) – automatically added for Cloud Run runtime.
   - **Cloud Run Invoker** (`roles/run.invoker`) – optional if you plan to make the service public; otherwise, manage access through IAM.
   - **Datastore User** (`roles/datastore.user`) – required for Firestore access.
   - **Storage Object Admin** (`roles/storage.objectAdmin`) – required for uploading and listing receipts in Cloud Storage.
   - **Logging > Logs Writer** (`roles/logging.logWriter`) – required if you enable the optional Cloud Logging appender.
   - **Secret Manager Secret Accessor** (`roles/secretmanager.secretAccessor`) – only if your service uses secrets.
4. Finish creation and note the service account email (`SA_EMAIL`).

### CLI

```bash
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud iam service-accounts create "$SA_NAME" \
  --project "$PROJECT_ID" \
  --description "Runtime SA for Cloud Run" \
  --display-name "Cloud Run Runtime"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/run.invoker"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/datastore.user"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/secretmanager.secretAccessor"  # optional

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/logging.logWriter"  # required for optional Cloud Logging output
```

> Cloud Run adds `roles/run.serviceAgent` automatically when the service is deployed. Include additional roles your app needs (Pub/Sub, Storage, etc.).

### How Cloud Run uses the service account

Cloud Run automatically injects the attached service account as [Application Default Credentials](https://cloud.google.com/docs/authentication/provide-credentials-adc). The platform exchanges that identity for short-lived OAuth tokens whenever the web app talks to Firestore, Secret Manager, or other Google APIs. Because of this managed flow:

- **Do not** provide a JSON key file to Cloud Run. Leaving `FIRESTORE_CREDENTIALS` unset is the correct configuration—the Firestore SDK picks up the runtime service account automatically.
- The deployment script (`scripts/deploy_cloud_run.sh`) selects the `cloud-run-runtime` account by default, but you can swap in any other service account that has `roles/datastore.user`.
- Only create key files for local development or if you plan to run the container outside Google Cloud. In those scenarios, point `FIRESTORE_CREDENTIALS_FILE` at the downloaded JSON and source `./scripts/load_local_secrets.sh`.

---

## 4. Build and Push the Container Image

### Console (Cloud Build)

1. Open **Cloud Build** → **Builds** → **Create build**.
2. Configure a build trigger or run a manual build pointing to the repository.
3. Ensure the `Dockerfile` is located at the repo root or provide the correct path.
4. Set the Artifact Registry repository as the build output.

### CLI

From the repository root:

```bash
PROJECT_ID="..."
REGION="..."
SERVICE_NAME="..."
IMAGE_REPO="${REGION}-docker.pkg.dev/${PROJECT_ID}/web"
IMAGE_TAG="${IMAGE_REPO}/${SERVICE_NAME}:$(date +%Y%m%d-%H%M%S)"

gcloud artifacts repositories create web \
  --repository-format=docker \
  --location="$REGION" \
  --description="Container images for Cloud Run" 2>/dev/null || true

# Build and push with Cloud Build
 gcloud builds submit \
  --tag "$IMAGE_TAG" \
  --project "$PROJECT_ID"
```

> Replace the build command if you prefer using `docker build` and `docker push`.

---

## 5. Deploy to Cloud Run

### Console

1. Navigate to **Cloud Run** → **Create service**.
2. Choose **Deploy one revision from an existing container image**.
3. Select the `IMAGE_URI` built in the previous step.
4. Set the service name (`SERVICE_NAME`) and region (`REGION`).
5. Under **Authentication**, choose whether to allow unauthenticated invocations.
6. Expand **Security** → **Service account** and select the runtime service account (`SA_EMAIL`).
7. Set environment variables (at a minimum `FIRESTORE_ENABLED=true`, `FIRESTORE_PROJECT_ID`, `SPRING_PROFILES_ACTIVE=prod,oauth`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GCS_ENABLED=true`, `GCS_PROJECT_ID`, `GCS_BUCKET`, and `RECEIPT_PROCESSOR_BASE_URL` pointing to the Cloud Run receipt processor URL). Keep `RECEIPT_PROCESSOR_USE_ID_TOKEN=true` so the web app authenticates with the processor automatically. The service logs to stdout/stderr unless you add `ENABLE_CLOUD_LOGGING=true`—only enable it when the runtime service account has `logging.logEntries.create`.
8. Configure CPU/Memory limits and concurrency as required.
9. Click **Create** to deploy.

### CLI

Export the Google OAuth client credentials before running the deployment so the script can inject them automatically. The automation aborts if the credentials are missing because Google sign-in is required in production:

```bash
export GOOGLE_CLIENT_ID="your-oauth-client-id"
export GOOGLE_CLIENT_SECRET="your-oauth-client-secret"
```

```bash
IMAGE_URI="${IMAGE_TAG}"
SPRING_PROFILES="prod"
SPRING_PROFILES="${SPRING_PROFILES},oauth"
RECEIPT_PROCESSOR_BASE_URL="https://RECEIPT_SERVICE_HOSTNAME"  # Replace with the Cloud Run receipt processor URL
RECEIPT_PROCESSOR_AUDIENCE="${RECEIPT_PROCESSOR_BASE_URL}"
ENV_VARS="SPRING_PROFILES_ACTIVE=${SPRING_PROFILES},FIRESTORE_ENABLED=true,FIRESTORE_PROJECT_ID=${PROJECT_ID},GCS_ENABLED=true,GCS_PROJECT_ID=${PROJECT_ID},GCS_BUCKET=${GCS_BUCKET},GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID},GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET},RECEIPT_PROCESSOR_BASE_URL=${RECEIPT_PROCESSOR_BASE_URL},RECEIPT_PROCESSOR_AUDIENCE=${RECEIPT_PROCESSOR_AUDIENCE}"
# Append ENABLE_CLOUD_LOGGING=true when the service account can write directly to Cloud Logging
# ENV_VARS="${ENV_VARS},ENABLE_CLOUD_LOGGING=true"

 gcloud run deploy "$SERVICE_NAME" \
  --image "$IMAGE_URI" \
  --service-account "$SA_EMAIL" \
  --region "$REGION" \
  --platform managed \
  --allow-unauthenticated \
  --set-env-vars "$ENV_VARS" \
 --min-instances 0 \
  --max-instances 10
```

Adjust min/max instances, authentication, and environment variables as necessary. If access should be restricted, remove `--allow-unauthenticated` and grant IAM access explicitly. Keep `FIRESTORE_ENABLED=true` so self-registration remains available.

> The deployment script automatically ensures the detected web runtime service account can invoke the receipt processor. If you use a different identity for the web service, set `WEB_SERVICE_ACCOUNT`, or point the detection logic at the correct service via `WEB_SERVICE_NAME`/`WEB_SERVICE_REGION`, before running `scripts/deploy_receipt_processor.sh`. Use `ADDITIONAL_INVOKER_SERVICE_ACCOUNTS` for multiple callers.

---

## 6. Verify Firestore Connectivity

### Console

1. After deployment, open the Cloud Run service details page.
2. Check the **Logs** tab for successful Firestore interactions.
3. Confirm that the application has the correct environment variables (under the **Variables & Secrets** section).

### CLI

```bash
gcloud run services describe "$SERVICE_NAME" \
  --region "$REGION" \
  --format "value(status.url)"

gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=${SERVICE_NAME}" \
  --limit 20 \
  --format json
```

Ensure the service account has permissions for other Google Cloud products your app uses (Pub/Sub, Storage, etc.).

---

## 7. Map the Custom Domain (pklnd.pekelund.dev)

### Console

1. In Cloud Run, open **Manage custom domains** → **Add mapping**.
2. Select `pklnd.pekelund.dev` as the domain.
3. Choose the Cloud Run service and region.
4. Download the generated DNS records (typically an A or CNAME record pointing to Google-managed endpoints).
5. In Porkbun:
   - Log in and edit the DNS records for `pklnd.pekelund.dev`.
   - Replace the existing record with the values provided by Google (usually a CNAME to `ghs.googlehosted.com` or A records to `199.36.153.4/5/6/7`).
6. Wait for DNS propagation (can take up to an hour).

### CLI

```bash
gcloud beta run domain-mappings create \
  --service "$SERVICE_NAME" \
  --domain "$DOMAIN" \
  --region "$REGION"
```

After running the command, retrieve the DNS records:

```bash
gcloud beta run domain-mappings describe "$DOMAIN" \
  --region "$REGION"
```

Update Porkbun DNS with the returned records. TTL 300 seconds (5 minutes) is sufficient. Use Porkbun's "Records" tab for the subdomain and ensure no conflicting A/CNAME records remain.

---

## 8. Secure the Deployment

1. **HTTPS**: Cloud Run automatically provisions managed certificates once DNS is configured.
2. **IAM Policies**: Limit who can invoke or manage the service.
3. **Secrets**: Store sensitive configuration in Secret Manager and mount them as environment variables or volumes.
4. **VPC Access** (optional): If the application needs to reach private resources, configure a Serverless VPC Access connector.
5. **Monitoring**: Configure uptime checks, alerts, and dashboards in Cloud Monitoring to track application health.

---

## 9. Deploying Updates

Repeat the build and deploy steps with a new image tag. Cloud Run supports traffic splitting if you need gradual rollouts.

- Build: `gcloud builds submit --tag NEW_IMAGE_URI`
- Deploy: `gcloud run deploy SERVICE_NAME --image NEW_IMAGE_URI ...`

Rollback by redeploying a previous revision or shifting traffic in the Cloud Run UI.

---

## 10. Cleanup

To remove the Cloud Run service and supporting resources:

```bash
gcloud run services delete "$SERVICE_NAME" --region "$REGION"
gcloud artifacts repositories delete web --location "$REGION"
gcloud firestore databases delete --database="(default)"
```

Ensure you understand the impact on production data before running cleanup commands.

---

## Troubleshooting Tips

- **Permission denied accessing Firestore**: Verify `roles/datastore.user` is granted to the runtime service account and that the Firestore database exists.
- **Domain mapping pending**: Check Porkbun DNS records; ensure CNAME/A records match Cloud Run instructions and no conflicting records exist.
- **Cold starts or latency issues**: Increase min instances or adjust concurrency.
- **Build failures**: Inspect Cloud Build logs; confirm Dockerfile path and environment variables.
- **Frequent 404s from `APIs-Google` with `__GCP_CloudEventsMode=GCS_NOTIFICATION`**: Cloud Storage periodically verifies that
  the push endpoint attached to your bucket is reachable. Google sends a handshake request to the service URL root (`/`) with
  that query parameter and expects any `2xx` response. The receipt processor now accepts the handshake, but you will still see
  the calls in access logs. They are normal and do not indicate user traffic or configuration errors.

For advanced setups (CI/CD, multiple services, staging environments), extend these instructions with additional automation, Terraform, or Cloud Deploy pipelines.
