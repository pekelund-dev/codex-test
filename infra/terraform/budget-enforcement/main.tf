provider "google" {
  project = var.project_id
  region  = var.region
}

# Enable required APIs
# Note: cloudbuild.googleapis.com is required for Cloud Functions deployment
resource "google_project_service" "required_services" {
  for_each = toset([
    "cloudfunctions.googleapis.com",
    "cloudbuild.googleapis.com",
    "pubsub.googleapis.com",
    "billingbudgets.googleapis.com",
  ])
  
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

# Create Pub/Sub topic for budget alerts
resource "google_pubsub_topic" "budget_alerts" {
  name    = var.budget_topic_name
  project = var.project_id

  depends_on = [google_project_service.required_services]
}

# Create service account for the Cloud Function
resource "google_service_account" "budget_enforcer" {
  account_id   = var.function_service_account
  display_name = "Budget Enforcement Function"
  project      = var.project_id
}

# Grant Cloud Run Admin permission to the function's service account
resource "google_project_iam_member" "function_run_admin" {
  project = var.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.budget_enforcer.email}"
}

# Grant Cloud Run Viewer permission (to list services)
resource "google_project_iam_member" "function_run_viewer" {
  project = var.project_id
  role    = "roles/run.viewer"
  member  = "serviceAccount:${google_service_account.budget_enforcer.email}"
}

# Grant service account user permission (to act as the service account)
resource "google_project_iam_member" "function_sa_user" {
  project = var.project_id
  role    = "roles/iam.serviceAccountUser"
  member  = "serviceAccount:${google_service_account.budget_enforcer.email}"
}

# Grant Pub/Sub subscriber permission
resource "google_pubsub_subscription_iam_member" "function_subscriber" {
  subscription = google_pubsub_subscription.budget_alerts_sub.name
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:${google_service_account.budget_enforcer.email}"
}

# Create Pub/Sub subscription for the Cloud Function
resource "google_pubsub_subscription" "budget_alerts_sub" {
  name    = "${var.budget_topic_name}-sub"
  topic   = google_pubsub_topic.budget_alerts.name
  project = var.project_id

  # Acknowledge messages after 60 seconds if not processed
  ack_deadline_seconds = 60

  # Retry policy for failed deliveries
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }

  depends_on = [google_project_service.required_services]
}

# Cloud Storage bucket for function source code
resource "google_storage_bucket" "function_source" {
  name                        = "${var.project_id}-budget-enforcer-source"
  location                    = var.region
  project                     = var.project_id
  uniform_bucket_level_access = true
  force_destroy               = true

  depends_on = [google_project_service.required_services]
}

# Archive the function source code
data "archive_file" "function_source" {
  type        = "zip"
  output_path = "${path.module}/budget-enforcer.zip"
  source_dir  = "${path.module}/../../functions/budget-enforcer"
}

# Upload function source to Cloud Storage
resource "google_storage_bucket_object" "function_source" {
  name   = "budget-enforcer-${data.archive_file.function_source.output_md5}.zip"
  bucket = google_storage_bucket.function_source.name
  source = data.archive_file.function_source.output_path
}

# Deploy Cloud Function
resource "google_cloudfunctions_function" "budget_enforcer" {
  name    = var.function_name
  runtime = "python311"
  project = var.project_id
  region  = var.region

  available_memory_mb   = 256
  timeout               = 60
  entry_point           = "stop_services"
  service_account_email = google_service_account.budget_enforcer.email

  source_archive_bucket = google_storage_bucket.function_source.name
  source_archive_object = google_storage_bucket_object.function_source.name

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = google_pubsub_topic.budget_alerts.id
  }

  environment_variables = {
    PROJECT_ID = var.project_id
    REGION     = var.region
  }

  depends_on = [
    google_project_service.required_services,
    google_project_iam_member.function_run_admin,
    google_project_iam_member.function_run_viewer,
  ]
}

# Create billing budget with Pub/Sub notifications
# Note: This requires billing account permissions and may need to be created manually
# or via gcloud CLI if the Terraform service account lacks billing permissions
resource "google_billing_budget" "monthly_budget" {
  count = var.create_budget ? 1 : 0

  billing_account = var.billing_account_id
  display_name    = var.budget_display_name

  budget_filter {
    projects = ["projects/${data.google_project.project.number}"]
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = tostring(var.budget_amount)
    }
  }

  # Alert thresholds: 50%, 80%, 100%
  dynamic "threshold_rules" {
    for_each = var.threshold_rules
    content {
      threshold_percent = threshold_rules.value.percent
    }
  }

  # Enable Pub/Sub notifications
  # Note: google_pubsub_topic.budget_alerts.id returns the full path format
  # required by the budget service: projects/{project}/topics/{topic}
  all_updates_rule {
    pubsub_topic                     = google_pubsub_topic.budget_alerts.id
    schema_version                   = "1.0"
    disable_default_iam_recipients   = var.disable_default_iam_recipients
  }

  depends_on = [google_project_service.required_services]
}

# Get project data for budget configuration
data "google_project" "project" {
  project_id = var.project_id
}
