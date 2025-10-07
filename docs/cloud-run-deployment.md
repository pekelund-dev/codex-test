# Deploying the Web Application to Google Cloud Run

This guide walks through provisioning Google Cloud resources, deploying the application to Cloud Run, configuring DNS, and verifying connectivity with Firestore and other Google Cloud services. It contains both **Google Cloud Console** and **gcloud CLI** workflows. Use whichever best fits your workflow, but keep the variables consistent across both approaches.

> 📈 Need a visual reference before diving into the steps? Review the [System Architecture Schematics](./system-architecture-diagrams.md) for high-level diagrams that outline service relationships, data flows, and deployment pipelines.

---

## Prerequisites

1. **Google Cloud project** with billing enabled.
2. **gcloud CLI** installed locally and authenticated (`gcloud init`).
3. Access to the Porkbun DNS dashboard for the `pklnd.pekelund.dev` subdomain.
4. Container image registry access (Artifact Registry or Docker Hub) for storing the built application image.
5. Source repository cloned locally with access to the application code.

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
| `SHARED_FIRESTORE_PROJECT_ID` | Project that hosts the single Firestore database used by Cloud Run, Cloud Functions, and user registration. Defaults to `PROJECT_ID`. |

---

## Automation Scripts and Idempotency Guarantees

Prefer the repository scripts when you want a repeatable, idempotent rollout:

- `scripts/deploy_cloud_run.sh` provisions APIs, Artifact Registry, Firestore, the runtime service account, and the Cloud Run service. It skips resource creation when assets already exist so re-running the script keeps the current state intact.
- `scripts/deploy_cloud_function.sh` packages the function with Maven, enables every dependency, and aligns IAM permissions with the shared Firestore project. Existing buckets, databases, and bindings are detected so the script can be executed multiple times safely.
- `scripts/teardown_gcp_resources.sh` removes the Cloud Run service, Cloud Function, IAM bindings, and optional supporting infrastructure. It tolerates partially deleted projects and only removes what is present. Set `DELETE_SERVICE_ACCOUNTS=true` and/or `DELETE_ARTIFACT_REPO=true` when you also want to purge the associated identities or container registry.

The rest of this document mirrors what the scripts perform under the hood if you prefer to click through the console or run individual `gcloud` commands.

---

## 1. Enable Required APIs

### Console

1. Open **Cloud Console** → **APIs & Services** → **Library**.
2. Enable the following APIs if they are not already enabled:
   - Cloud Run Admin API
   - Cloud Build API
   - Artifact Registry API (or Container Registry if you still use it)
   - Firestore API
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

The same Firestore database stores both the **user registration** data managed by the Cloud Run web application and the **receipt extraction** documents persisted by the Cloud Function. To keep data consistent:

1. Use the same `PROJECT_ID` (or explicitly set `SHARED_FIRESTORE_PROJECT_ID`) for every deployment script and console workflow.
2. Keep the `users` collection for authentication data and `receiptExtractions` for parsed receipts in the same database.
3. Reuse the runtime service accounts created in this guide (or grant them `roles/datastore.user`) so the Cloud Run service, Cloud Function, and any local admin scripts can all read/write the shared documents.
4. When setting environment variables, ensure `FIRESTORE_PROJECT_ID` (Cloud Run) and `RECEIPT_FIRESTORE_PROJECT_ID` (Cloud Function) point to this project. If you override collection names, update both components accordingly.

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
7. Set environment variables (at a minimum `FIRESTORE_ENABLED=true`, `FIRESTORE_PROJECT_ID`, `SPRING_PROFILES_ACTIVE=prod,oauth`, `GOOGLE_CLIENT_ID`, and `GOOGLE_CLIENT_SECRET`).
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
ENV_VARS="SPRING_PROFILES_ACTIVE=${SPRING_PROFILES},FIRESTORE_ENABLED=true,FIRESTORE_PROJECT_ID=${PROJECT_ID},GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID},GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}"

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

For advanced setups (CI/CD, multiple services, staging environments), extend these instructions with additional automation, Terraform, or Cloud Deploy pipelines.
