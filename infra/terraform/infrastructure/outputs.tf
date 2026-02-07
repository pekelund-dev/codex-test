output "bucket_name" {
  description = "Google Cloud Storage bucket for receipt uploads"
  value       = google_storage_bucket.receipts.name
}

output "web_service_account_email" {
  description = "Runtime service account for the web application"
  value       = google_service_account.web.email
}

output "receipt_service_account_email" {
  description = "Runtime service account for the receipt processor"
  value       = google_service_account.receipt.email
}

output "web_artifact_registry" {
  description = "Artifact Registry repository for the web image"
  value       = google_artifact_registry_repository.web.id
}

output "receipt_artifact_registry" {
  description = "Artifact Registry repository for the receipt processor image"
  value       = google_artifact_registry_repository.receipts.id
}

output "app_secret_name" {
  description = "Unified Secret Manager secret id for application credentials"
  value       = google_secret_manager_secret.app_config.secret_id
}

output "billing_alerts_topic" {
  description = "Pub/Sub topic for billing alerts"
  value       = google_pubsub_topic.billing_alerts.name
}

output "pubsub_invoker_service_account" {
  description = "Service account email for Pub/Sub to invoke Cloud Run"
  value       = google_service_account.pubsub_invoker.email
}
