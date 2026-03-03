# Automate Firestore backups via Cloud Scheduler

- [x] infra/terraform/infrastructure/backups.tf created
  - GCS bucket pklnd-firestore-backups-{project_id} with 30-day lifecycle
  - Service account firestore-backup with datastore export admin role
  - Cloud Scheduler job running daily at 02:00 Europe/Stockholm
