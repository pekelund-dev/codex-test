# Backup and retention strategy

This service stores user data in Firestore and receipt files in Cloud Storage. Use the guidance below to keep regular
backups and ensure retention policies align with your compliance requirements.

## Firestore exports

1. Create a Cloud Storage bucket dedicated to Firestore exports (e.g. `gs://<project>-firestore-backups`).
2. Run manual exports as needed:

   ```bash
   gcloud firestore export gs://<project>-firestore-backups/exports/$(date +%Y-%m-%d)
   ```

3. For automated exports, schedule a Cloud Scheduler job that invokes a Cloud Run job or Cloud Function which runs the
   same `gcloud firestore export` command.

## Cloud Storage lifecycle policies

Apply a lifecycle policy to the backup bucket to control retention. A typical policy might:

- Keep daily exports for 30 days.
- Keep weekly exports for 90 days.
- Delete objects older than 180 days.

Define lifecycle rules in Terraform or via `gcloud storage buckets update` to ensure old exports are removed
automatically.

## Receipt uploads

Receipt uploads live in the main receipts bucket configured by `GCS_BUCKET`. If you need retention controls, apply a
lifecycle policy that matches your organisation’s data retention rules (for example, retain files for 365 days, or
indefinitely for audit requirements).
