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
  bucket_name             = var.bucket_name
  web_repository_id       = var.web_repository_id
  receipts_repository_id  = var.receipts_repository_id
  web_service_account     = var.web_service_account
  receipt_service_account = var.receipt_service_account
  upload_service_account  = var.upload_service_account
  web_service_name        = var.web_service_name
  receipt_service_name    = var.receipt_service_name
  config_secret_name      = var.config_secret_id

  default_web_service_account_email     = "${var.web_service_account}@${var.project_id}.iam.gserviceaccount.com"
  default_receipt_service_account_email = "${var.receipt_service_account}@${var.project_id}.iam.gserviceaccount.com"
  default_upload_service_account_email  = "${var.upload_service_account}@${var.project_id}.iam.gserviceaccount.com"

  web_service_account_email     = var.manage_service_accounts ? google_service_account.web_runtime[0].email : coalesce(var.web_service_account_email, local.default_web_service_account_email)
  receipt_service_account_email = var.manage_service_accounts ? google_service_account.receipt_runtime[0].email : coalesce(var.receipt_service_account_email, local.default_receipt_service_account_email)
  manage_upload_service_account = var.manage_service_accounts || var.upload_service_account_email != null
  upload_service_account_email  = var.manage_service_accounts ? google_service_account.upload[0].email : var.upload_service_account_email
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
  location      = var.region
  project       = var.project_id
  repository_id = local.web_repository_id
  format        = "DOCKER"

  cleanup_policies {
    id     = "retain-latest-10"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }

}

resource "google_artifact_registry_repository" "receipts" {
  location      = var.region
  project       = var.project_id
  repository_id = local.receipts_repository_id
  format        = "DOCKER"

  cleanup_policies {
    id     = "retain-latest-10"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }

}

resource "google_service_account" "web_runtime" {
  count = var.manage_service_accounts ? 1 : 0

  account_id   = local.web_service_account
  project      = var.project_id
  display_name = "pklnd web runtime"

}

resource "google_service_account" "receipt_runtime" {
  count = var.manage_service_accounts ? 1 : 0

  account_id   = local.receipt_service_account
  project      = var.project_id
  display_name = "Receipt processor runtime"

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
  member    = "serviceAccount:${local.web_service_account_email}"
}

resource "google_secret_manager_secret_iam_member" "config_receipt" {
  secret_id = google_secret_manager_secret.pklnd_config.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${local.receipt_service_account_email}"
}

resource "google_service_account" "upload" {
  count = var.manage_service_accounts ? 1 : 0

  account_id   = local.upload_service_account
  project      = var.project_id
  display_name = "Receipt uploads"

}

resource "google_project_iam_member" "web_firestore" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${local.web_service_account_email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "web_storage" {
  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${local.web_service_account_email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "receipt_firestore" {
  project = var.project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${local.receipt_service_account_email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "receipt_storage" {
  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${local.receipt_service_account_email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "receipt_vertex" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${local.receipt_service_account_email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "receipt_logging" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${local.receipt_service_account_email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_project_iam_member" "upload_storage" {
  count = local.manage_upload_service_account ? 1 : 0

  project = var.project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${local.upload_service_account_email}"
  depends_on = [google_project_service.pklnd_services]
}

resource "google_storage_bucket_iam_member" "web_bucket_admin" {
  bucket = google_storage_bucket.receipts.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${local.web_service_account_email}"
}

resource "google_storage_bucket_iam_member" "receipt_bucket_admin" {
  bucket = google_storage_bucket.receipts.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${local.receipt_service_account_email}"
}

resource "google_storage_bucket_iam_member" "upload_bucket_admin" {
  count = local.manage_upload_service_account ? 1 : 0

  bucket = google_storage_bucket.receipts.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${local.upload_service_account_email}"
}

resource "google_cloud_run_service" "receipt_processor" {
  name     = local.receipt_service_name
  location = var.region
  project  = var.project_id

  autogenerate_revision_name = true

  template {
    spec {
      service_account_name = local.receipt_service_account_email

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
      service_account_name = local.web_service_account_email

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
  member   = "serviceAccount:${local.web_service_account_email}"
}

output "bucket_name" {
  description = "Receipts bucket for production"
  value       = google_storage_bucket.receipts.name
}

output "web_service_account_email" {
  description = "Runtime service account for the web app"
  value       = local.web_service_account_email
}

output "receipt_service_account_email" {
  description = "Runtime service account for the receipt processor"
  value       = local.receipt_service_account_email
}

output "upload_service_account_email" {
  description = "Service account used for direct receipt uploads"
  value       = local.upload_service_account_email
}

output "config_secret" {
  description = "Secret Manager entry holding all production configuration values"
  value       = google_secret_manager_secret.pklnd_config.secret_id
}

output "web_repository" {
  description = "Artifact Registry repository for the web app"
  value       = google_artifact_registry_repository.web.repository_id
}

output "receipt_repository" {
  description = "Artifact Registry repository for the receipt processor"
  value       = google_artifact_registry_repository.receipts.repository_id
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
