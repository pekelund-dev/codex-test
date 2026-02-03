# Backup and retention strategy

This service stores user data in Firestore and receipt files in Cloud Storage. Use the guidance below to keep regular
backups and ensure retention policies align with your compliance requirements.

## Firestore exports

1. Create a Cloud Storage bucket dedicated to Firestore exports (e.g. `gs://<project>-firestore-backups`).
2. Configure the web app with the bucket by setting `FIRESTORE_BACKUP_BUCKET` (and optionally
   `FIRESTORE_BACKUP_PREFIX`). This enables the admin dashboard backup/restore controls.
3. Run manual exports as needed:

   ```bash
   gcloud firestore export gs://<project>-firestore-backups/exports/$(date +%Y-%m-%d)
   ```

4. For automated exports, schedule a Cloud Scheduler job that invokes a Cloud Run job or Cloud Function which runs the
   same `gcloud firestore export` command.

## Admin dashboard backups and restores

Administrators can trigger Firestore exports and imports directly from the dashboard when Firestore is enabled and
`FIRESTORE_BACKUP_BUCKET` is configured. The restore flow expects a `gs://` path pointing at an export folder created by
the Firestore export operation.

> The runtime service account must have access to the backup bucket and the Firestore Admin API must be enabled for
> exports/imports to succeed.

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
