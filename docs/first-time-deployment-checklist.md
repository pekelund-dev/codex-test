# First-time Deployment Checklist

Complete these tasks before using any of the automation helpers (`scripts/deploy_cloud_run.sh`, `scripts/deploy_cloud_run_test.sh`, `scripts/deploy_receipt_processor.sh`, `scripts/deploy_receipt_processor_test.sh`). The steps only need to be performed once per workstation unless you rotate credentials or change projects.

## 1. Authenticate with Google Cloud
- Install the [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) for your platform.
- Run `gcloud auth login` and `gcloud auth application-default login` with an account that can manage Cloud Run, IAM, Firestore, Cloud Storage, and Artifact Registry in both staging and production projects.
- Verify `gcloud config list` points at the correct default project or rely on the per-environment variables in the next section so the scripts can switch automatically.

## 2. Load environment variable profiles
- Export the variables listed in [docs/environment-variables.md](./environment-variables.md) so each script can determine the project, region, and shared resource identifiers for staging and production.
- Use the `PROD_`/`STAGING_` prefixes consistently (for example `STAGING_PROJECT_ID`, `PROD_REGION`, `PROD_GCS_BUCKET`). Set `SHARED_INFRA_PROJECT_ID` (and its prefixed variants) if Firestore or Cloud Storage live in a dedicated project.
- Keep sensitive values (OAuth client secrets, JSON key paths) in shell startup files or `.env` snippets that you source before running the helpers.

## 3. Provision shared Google Cloud services
- Follow the service setup guide in [docs/gcp-services.md](./gcp-services.md) to enable APIs and create the shared infrastructure: Firestore database, receipt storage bucket, Artifact Registry repositories, and IAM service accounts.
- Confirm the Cloud Run runtime and receipt processor service accounts have the necessary roles before the first deployment so the scripts can reuse them without re-provisioning.

## 4. Prepare the repository checkout
- Clone this repository locally and ensure the helper scripts retain execute permissions (`chmod +x scripts/*.sh` if needed).
- Install supporting command-line tools referenced by the scripts, such as `gsutil` and `bq`, which are bundled with the Google Cloud SDK. Keep them current with `gcloud components update`.

## 5. Optional local secrets workflow
- Store downloaded service-account JSON files or OAuth credentials outside of the repository (for example `~/.config/pklnd/`).
- Point the helper scripts at those files using the environment variables documented in [docs/environment-variables.md](./environment-variables.md) and source `scripts/load_local_secrets.sh` when you want to populate runtime exports without committing secrets.

After completing this checklist, deploy the staging environment first using the `_test` wrappers. Once verified, reuse the staged container image when promoting to production so both environments run the identical build.
