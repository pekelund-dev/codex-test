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
