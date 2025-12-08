provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

locals {
  firestore_project_id = var.firestore_project_id != "" ? var.firestore_project_id : var.project_id
  gcs_project_id       = var.gcs_project_id != "" ? var.gcs_project_id : var.project_id
  vertex_ai_project_id = var.vertex_ai_project_id != "" ? var.vertex_ai_project_id : var.project_id
  vertex_ai_location   = var.vertex_ai_location != "" ? var.vertex_ai_location : var.region
  logging_project_id   = var.logging_project_id != "" ? var.logging_project_id : var.project_id
}

resource "google_cloud_run_v2_service" "receipts" {
  name     = var.receipt_service_name
  location = var.region
  project  = var.project_id

  template {
    service_account = var.receipt_service_account_email

    containers {
      image = var.receipt_image

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }

      env {
        name  = "PROJECT_ID"
        value = var.project_id
      }

      env {
        name  = "VERTEX_AI_PROJECT_ID"
        value = local.vertex_ai_project_id
      }

      env {
        name  = "VERTEX_AI_LOCATION"
        value = local.vertex_ai_location
      }

      env {
        name  = "VERTEX_AI_GEMINI_MODEL"
        value = var.vertex_ai_gemini_model
      }

      env {
        name  = "RECEIPT_FIRESTORE_COLLECTION"
        value = "receiptExtractions"
      }

      env {
        name  = "RECEIPT_FIRESTORE_ITEM_COLLECTION"
        value = "receiptItems"
      }

      env {
        name  = "RECEIPT_FIRESTORE_ITEM_STATS_COLLECTION"
        value = "receiptItemStats"
      }

      env {
        name  = "LOGGING_PROJECT_ID"
        value = local.logging_project_id
      }

      env {
        name  = "APP_CONFIG_SECRET_NAME"
        value = var.secret_name
      }

      env {
        name  = "AI_STUDIO_API_KEY"
        value = var.ai_studio_api_key
      }
    }

    scaling {
      min_instance_count = 0
      max_instance_count = 5
    }
  }

  traffic {
    percent = 100
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
  }
}

resource "google_cloud_run_v2_service" "web" {
  name     = var.web_service_name
  location = var.region
  project  = var.project_id

  template {
    service_account = var.web_service_account_email

    containers {
      image = var.web_image

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod,oauth"
      }

      env {
        name  = "FIRESTORE_ENABLED"
        value = "true"
      }

      env {
        name  = "FIRESTORE_PROJECT_ID"
        value = local.firestore_project_id
      }

      env {
        name  = "GOOGLE_CLIENT_ID"
        value = var.google_client_id
      }

      env {
        name  = "GOOGLE_CLIENT_SECRET"
        value = var.google_client_secret
      }

      env {
        name  = "GCS_ENABLED"
        value = "true"
      }

      env {
        name  = "GCS_PROJECT_ID"
        value = local.gcs_project_id
      }

      env {
        name  = "GCS_BUCKET"
        value = var.bucket_name
      }

      env {
        name  = "RECEIPT_PROCESSOR_BASE_URL"
        value = google_cloud_run_v2_service.receipts.uri
      }

      env {
        name  = "RECEIPT_PROCESSOR_AUDIENCE"
        value = google_cloud_run_v2_service.receipts.uri
      }

      env {
        name  = "APP_CONFIG_SECRET_NAME"
        value = var.secret_name
      }
    }

    scaling {
      min_instance_count = 0
      max_instance_count = 10
    }
  }

  traffic {
    percent = 100
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
  }
}

resource "google_cloud_run_v2_service_iam_member" "web_public" {
  count    = var.allow_unauthenticated_web ? 1 : 0
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.web.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_service_iam_member" "receipt_web_invoker" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.receipts.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.web_service_account_email}"
}

resource "google_cloud_run_v2_service_iam_member" "receipt_self_invoker" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.receipts.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${var.receipt_service_account_email}"
}

# NOTE: Cloud Run v2 domain mappings are not fully supported by Terraform, so
# manage them manually after applying this stack.
# To map a custom domain, use the Google Cloud Console or the gcloud CLI:
#   gcloud run domain-mappings create \ \
#     --service <SERVICE_NAME> \ \
#     --domain <CUSTOM_DOMAIN> \ \
#     --region <REGION>
# You can delete an existing mapping if you need to re-point it:
#   gcloud run domain-mappings delete --domain <CUSTOM_DOMAIN> --region <REGION>

output "web_service_url" {
  value       = google_cloud_run_v2_service.web.uri
  description = "URL for the web Cloud Run service"
}

output "receipt_service_url" {
  value       = google_cloud_run_v2_service.receipts.uri
  description = "URL for the receipt processor Cloud Run service"
}

output "web_service_name" {
  value       = google_cloud_run_v2_service.web.name
  description = "Name of the web Cloud Run service"
}

output "receipt_service_name" {
  value       = google_cloud_run_v2_service.receipts.name
  description = "Name of the receipt Cloud Run service"
}

output "region" {
  value       = var.region
  description = "Region where services are deployed"
}
