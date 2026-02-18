provider "google" {
  project               = var.project_id
  region                = var.region
  user_project_override = true
  billing_project       = var.project_id
}

locals {
  receipts_bucket = var.bucket_name != "" ? var.bucket_name : "pklnd-receipts-${var.project_id}"
  api_services = [
    "run.googleapis.com",
    "cloudbuild.googleapis.com",
    "artifactregistry.googleapis.com",
    "firestore.googleapis.com",
    "storage.googleapis.com",
    "secretmanager.googleapis.com",
    "logging.googleapis.com",
    "aiplatform.googleapis.com",
    "pubsub.googleapis.com",
    "billingbudgets.googleapis.com",
  ]
}

resource "google_project_service" "services" {
  for_each           = toset(local.api_services)
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_firestore_database" "receipts" {
  name           = var.firestore_database_name
  project        = var.project_id
  location_id    = var.firestore_location
  type           = "FIRESTORE_NATIVE"
  concurrency_mode = "OPTIMISTIC"

  depends_on = [google_project_service.services]
}

resource "google_storage_bucket" "receipts" {
  name                        = local.receipts_bucket
  project                     = var.project_id
  location                    = var.region
  uniform_bucket_level_access = true
}

resource "google_artifact_registry_repository" "web" {
  repository_id = var.web_artifact_repo
  location      = var.region
  project       = var.project_id
  format        = "DOCKER"
  description   = "Container images for the web service"

  depends_on = [google_project_service.services]
}

resource "google_artifact_registry_repository" "receipts" {
  repository_id = var.receipt_artifact_repo
  location      = var.region
  project       = var.project_id
  format        = "DOCKER"
  description   = "Container images for the receipt processor"

  depends_on = [google_project_service.services]
}

resource "google_service_account" "web" {
  account_id   = var.web_service_account
  display_name = "Cloud Run web runtime"
  project      = var.project_id
}

resource "google_service_account" "receipt" {
  account_id   = var.receipt_service_account
  display_name = "Receipt processor runtime"
  project      = var.project_id
}

locals {
  web_roles = [
    "roles/datastore.user",
  ]
  receipt_roles = [
    "roles/datastore.user",
    "roles/aiplatform.user",
    "roles/logging.logWriter",
  ]
}

resource "google_project_iam_member" "web_roles" {
  for_each = toset(local.web_roles)
  project  = var.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.web.email}"
}

resource "google_project_iam_member" "receipt_roles" {
  for_each = toset(local.receipt_roles)
  project  = var.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.receipt.email}"
}

resource "google_storage_bucket_iam_member" "web_bucket_admin" {
  bucket = google_storage_bucket.receipts.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.web.email}"
}

resource "google_storage_bucket_iam_member" "receipt_bucket_admin" {
  bucket = google_storage_bucket.receipts.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.receipt.email}"
}

resource "google_secret_manager_secret" "app_config" {
  secret_id  = var.app_secret_name
  project    = var.project_id

  replication {
    auto {}
  }

  depends_on = [google_project_service.services]
}

resource "google_secret_manager_secret_version" "app_config" {
  count       = var.app_secret_json == "" ? 0 : 1
  secret      = google_secret_manager_secret.app_config.id
  secret_data = var.app_secret_json
}

resource "google_secret_manager_secret_iam_member" "web_secret_access" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.app_config.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.web.email}"
}

resource "google_secret_manager_secret_iam_member" "receipt_secret_access" {
  project   = var.project_id
  secret_id = google_secret_manager_secret.app_config.secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.receipt.email}"
}
