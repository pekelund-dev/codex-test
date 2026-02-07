# Pub/Sub topic for billing alerts
resource "google_pubsub_topic" "billing_alerts" {
  name    = "billing-alerts"
  project = var.project_id

  depends_on = [google_project_service.services]
}

# Service account for Pub/Sub to invoke Cloud Run
resource "google_service_account" "pubsub_invoker" {
  account_id   = "cloud-run-pubsub-invoker"
  display_name = "Cloud Run Pub/Sub Invoker"
  project      = var.project_id
}

# Grant the service account permission to publish to the billing alerts topic
# This is required for the billing alert service to publish to the topic
resource "google_pubsub_topic_iam_member" "billing_publisher" {
  project = var.project_id
  topic   = google_pubsub_topic.billing_alerts.name
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${google_service_account.pubsub_invoker.email}"

  depends_on = [google_service_account.pubsub_invoker]
}

# Billing account data source to reference in budget
# Note: This requires billing.accounts.get permission
data "google_billing_account" "account" {
  display_name = var.billing_account_display_name
  open         = true
}

# Budget alert configuration
resource "google_billing_budget" "budget" {
  count = var.enable_budget_alert ? 1 : 0

  billing_account = data.google_billing_account.account.id
  display_name    = "Monthly Budget Alert"

  budget_filter {
    projects = ["projects/${data.google_project.project.number}"]
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = tostring(var.budget_amount)
    }
  }

  threshold_rules {
    threshold_percent = 0.5  # Alert at 50%
  }

  threshold_rules {
    threshold_percent = 0.75  # Alert at 75%
  }

  threshold_rules {
    threshold_percent = 0.9  # Alert at 90%
  }

  threshold_rules {
    threshold_percent = 1.0   # Alert at 100% - trigger shutdown
  }

  all_updates_rule {
    pubsub_topic = google_pubsub_topic.billing_alerts.id
    disable_default_iam_recipients = false
  }
}

# Project data source for budget configuration
data "google_project" "project" {
  project_id = var.project_id
}
