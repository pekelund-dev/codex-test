# Automate Firestore backups via Cloud Scheduler
# Daily export of Firestore to GCS, with a 30-day retention lifecycle rule on exports.

resource "google_project_service" "backup_services" {
  for_each = toset([
    "cloudscheduler.googleapis.com",
    "firestore.googleapis.com",
  ])
  service            = each.key
  project            = var.project_id
  disable_on_destroy = false
}

resource "google_storage_bucket" "firestore_backups" {
  name                        = "pklnd-firestore-backups-${var.project_id}"
  location                    = var.region
  project                     = var.project_id
  force_destroy               = false
  uniform_bucket_level_access = true

  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type = "Delete"
    }
  }

  depends_on = [google_project_service.backup_services]
}

resource "google_service_account" "firestore_backup" {
  account_id   = "firestore-backup"
  display_name = "Firestore backup scheduler"
  project      = var.project_id
}

resource "google_project_iam_member" "firestore_backup_datastore_owner" {
  project = var.project_id
  role    = "roles/datastore.importExportAdmin"
  member  = "serviceAccount:${google_service_account.firestore_backup.email}"
}

resource "google_storage_bucket_iam_member" "firestore_backup_writer" {
  bucket = google_storage_bucket.firestore_backups.name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${google_service_account.firestore_backup.email}"
}

resource "google_cloud_scheduler_job" "firestore_backup" {
  name             = "firestore-daily-backup"
  description      = "Daily Firestore export to GCS"
  schedule         = "0 2 * * *"
  time_zone        = "Europe/Stockholm"
  project          = var.project_id
  region           = var.region
  attempt_deadline = "320s"

  http_target {
    http_method = "POST"
    uri         = "https://firestore.googleapis.com/v1/projects/${var.project_id}/databases/(default):exportDocuments"
    body = base64encode(jsonencode({
      outputUriPrefix = "gs://${google_storage_bucket.firestore_backups.name}/daily"
    }))
    oauth_token {
      service_account_email = google_service_account.firestore_backup.email
    }
  }

  depends_on = [google_project_service.backup_services]
}
