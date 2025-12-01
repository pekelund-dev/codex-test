terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

locals {
  bucket_name             = "pklnd-receipts-${var.env_name}-${var.project_id}"
  web_repository_id       = "web-${var.env_name}"
  receipts_repository_id  = "receipts-${var.env_name}"
  web_service_account     = "cloud-run-runtime-${var.env_name}"
  receipt_service_account = "receipt-processor-${var.env_name}"
  upload_service_account  = "receipt-uploads-${var.env_name}"
  web_service_name        = "pklnd-web-${var.env_name}"
  receipt_service_name    = "pklnd-receipts-${var.env_name}"
  config_secret_name      = "pklnd-config-${var.env_name}"
}

resource "google_project_service" "pklnd_services" {
  for_each = toset([
    "run.googleapis.com",
    "cloudbuild.googleapis.com",
    "artifactregistry.googleapis.com",
    "aiplatform.googleapis.com",
    "storage.googleapis.com",
    "firestore.googleapis.com",
    "secretmanager.googleapis.com",
  ])

  project = var.project_id
  service = each.key
}

resource "google_storage_bucket" "receipts" {
  name          = local.bucket_name
  location      = var.region
  project       = var.project_id
  force_destroy = false

  uniform_bucket_level_access = true

  lifecycle_rule {
    action {
      type = "Delete"
    }
    condition {
      age        = 365
      with_state = "ARCHIVED"
    }
  }

}

resource "google_artifact_registry_repository" "web" {
  location        = var.region
  project         = var.project_id
  repository_id   = local.web_repository_id
  format          = "DOCKER"
  cleanup_policies {
    id     = "retain-latest-10"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }

}

resource "google_artifact_registry_repository" "receipts" {
  location        = var.region
  project         = var.project_id
  repository_id   = local.receipts_repository_id
  format          = "DOCKER"
  cleanup_policies {
    id     = "retain-latest-10"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }

}

resource "google_service_account" "web_runtime" {
  account_id   = local.web_service_account
  project      = var.project_id
  display_name = "pklnd web runtime (${var.env_name})"

}

resource "google_service_account" "receipt_runtime" {
  account_id   = local.receipt_service_account
  project      = var.project_id
  display_name = "Receipt processor runtime (${var.env_name})"

}

resource "google_secret_manager_secret" "pklnd_config" {
  secret_id = local.config_secret_name
  project   = var.project_id

  replication {
    auto {}
  }

}

resource "google_secret_manager_secret_iam_member" "config_web" {
  secret_id = google_secret_manager_secret.pklnd_config.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.web_runtime.email}"
}

resource "google_secret_manager_secret_iam_member" "config_receipt" {
  secret_id = google_secret_manager_secret.pklnd_config.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.receipt_runtime.email}"
}

resource "google_service_account" "upload" {
  account_id   = local.upload_service_account
  project      = var.project_id
  display_name = "Receipt uploads (${var.env_name})"
}

resource "google_project_iam_member" "web_firestore" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.web_runtime.email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "web_storage" {
  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.web_runtime.email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "receipt_firestore" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.receipt_runtime.email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "receipt_storage" {
  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.receipt_runtime.email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "receipt_vertex" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${google_service_account.receipt_runtime.email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "receipt_logging" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.receipt_runtime.email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "upload_storage" {
  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.upload.email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_storage_bucket_iam_member" "web_bucket_admin" {
  bucket = google_storage_bucket.receipts.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.web_runtime.email}"
}

resource "google_storage_bucket_iam_member" "receipt_bucket_admin" {
  bucket = google_storage_bucket.receipts.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.receipt_runtime.email}"
}

resource "google_storage_bucket_iam_member" "upload_bucket_admin" {
  bucket = google_storage_bucket.receipts.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.upload.email}"
}

resource "google_cloud_run_service" "receipt_processor" {
  name     = local.receipt_service_name
  location = var.region
  project  = var.project_id

  autogenerate_revision_name = true

  template {
    spec {
      service_account_name = google_service_account.receipt_runtime.email

      containers {
        image = var.receipt_image
        env {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "prod"
        }
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }

  lifecycle {
    ignore_changes = [template[0].spec[0].containers[0].image]
  }

  depends_on = [google_project_service.pklnd_services]
}

resource "google_cloud_run_service" "web" {
  name     = local.web_service_name
  location = var.region
  project  = var.project_id

  autogenerate_revision_name = true

  template {
    spec {
      service_account_name = google_service_account.web_runtime.email

      containers {
        image = var.web_image
        env {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "prod"
        }
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }

  lifecycle {
    ignore_changes = [template[0].spec[0].containers[0].image]
  }

  depends_on = [google_project_service.pklnd_services]
}

resource "google_cloud_run_service_iam_member" "web_public" {
  count    = var.allow_web_unauthenticated ? 1 : 0
  location = google_cloud_run_service.web.location
  project  = google_cloud_run_service.web.project
  service  = google_cloud_run_service.web.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_service_iam_member" "receipt_public" {
  count    = var.allow_receipt_unauthenticated ? 1 : 0
  location = google_cloud_run_service.receipt_processor.location
  project  = google_cloud_run_service.receipt_processor.project
  service  = google_cloud_run_service.receipt_processor.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_service_iam_member" "receipt_invoker_web" {
  location = google_cloud_run_service.receipt_processor.location
  project  = google_cloud_run_service.receipt_processor.project
  service  = google_cloud_run_service.receipt_processor.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.web_runtime.email}"
}

output "bucket_name" {
  description = "Receipts bucket for the environment"
  value       = google_storage_bucket.receipts.name
}

output "web_service_account_email" {
  description = "Runtime service account for the web app"
  value       = google_service_account.web_runtime.email
}

output "receipt_service_account_email" {
  description = "Runtime service account for the receipt processor"
  value       = google_service_account.receipt_runtime.email
}

output "web_repository" {
  description = "Artifact Registry repository for the web app"
  value       = google_artifact_registry_repository.web.repository_id
}

output "receipt_repository" {
  description = "Artifact Registry repository for the receipt processor"
  value       = google_artifact_registry_repository.receipts.repository_id
}

output "upload_service_account_email" {
  description = "Service account used for direct receipt uploads"
  value       = google_service_account.upload.email
}

output "config_secret" {
  description = "Secret Manager entry holding all test configuration values"
  value       = google_secret_manager_secret.pklnd_config.secret_id
}

output "web_service_name" {
  description = "Cloud Run service name for the web app"
  value       = google_cloud_run_service.web.name
}

output "receipt_service_name" {
  description = "Cloud Run service name for the receipt processor"
  value       = google_cloud_run_service.receipt_processor.name
}

output "web_service_url" {
  description = "Deployed URL of the web Cloud Run service"
  value       = google_cloud_run_service.web.status[0].url
}

output "receipt_service_url" {
  description = "Deployed URL of the receipt processor Cloud Run service"
  value       = google_cloud_run_service.receipt_processor.status[0].url
}
