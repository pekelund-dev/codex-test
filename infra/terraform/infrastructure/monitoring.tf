# Cloud Monitoring alerts for errors, latency, and service unavailability

resource "google_project_service" "monitoring" {
  service            = "monitoring.googleapis.com"
  project            = var.project_id
  disable_on_destroy = false
}

# Notification channel (email) — use a variable so the address can be set per environment
variable "alert_email" {
  description = "Email address for Cloud Monitoring alert notifications"
  type        = string
  default     = ""
}

resource "google_monitoring_notification_channel" "email" {
  count        = var.alert_email != "" ? 1 : 0
  display_name = "Email alert channel"
  type         = "email"
  project      = var.project_id
  labels = {
    email_address = var.alert_email
  }
  depends_on = [google_project_service.monitoring]
}

locals {
  notification_channels = var.alert_email != "" ? [google_monitoring_notification_channel.email[0].name] : []
}

# Alert: web service error rate > 5% over 5 minutes
resource "google_monitoring_alert_policy" "web_error_rate" {
  display_name = "Web service error rate"
  project      = var.project_id
  combiner     = "OR"

  conditions {
    display_name = "HTTP 5xx rate > 5%"
    condition_threshold {
      filter          = "resource.type=\"cloud_run_revision\" AND resource.labels.service_name=\"${var.web_service_name}\" AND metric.type=\"run.googleapis.com/request_count\" AND metric.labels.response_code_class=\"5xx\""
      duration        = "300s"
      comparison      = "COMPARISON_GT"
      threshold_value = 0.05
      aggregations {
        alignment_period     = "60s"
        per_series_aligner   = "ALIGN_RATE"
        cross_series_reducer = "REDUCE_MEAN"
      }
    }
  }

  notification_channels = local.notification_channels
  depends_on            = [google_project_service.monitoring]
}

# Alert: web service P95 latency > 5s
resource "google_monitoring_alert_policy" "web_latency" {
  display_name = "Web service high latency"
  project      = var.project_id
  combiner     = "OR"

  conditions {
    display_name = "P95 request latency > 5s"
    condition_threshold {
      filter          = "resource.type=\"cloud_run_revision\" AND resource.labels.service_name=\"${var.web_service_name}\" AND metric.type=\"run.googleapis.com/request_latencies\""
      duration        = "300s"
      comparison      = "COMPARISON_GT"
      threshold_value = 5000
      aggregations {
        alignment_period     = "60s"
        per_series_aligner   = "ALIGN_PERCENTILE_99"
        cross_series_reducer = "REDUCE_MEAN"
      }
    }
  }

  notification_channels = local.notification_channels
  depends_on            = [google_project_service.monitoring]
}

# Alert: web service instance count is 0 (service unavailable)
resource "google_monitoring_alert_policy" "web_unavailable" {
  display_name = "Web service unavailable"
  project      = var.project_id
  combiner     = "OR"

  conditions {
    display_name = "No running instances"
    condition_threshold {
      filter          = "resource.type=\"cloud_run_revision\" AND resource.labels.service_name=\"${var.web_service_name}\" AND metric.type=\"run.googleapis.com/container/instance_count\""
      duration        = "120s"
      comparison      = "COMPARISON_LT"
      threshold_value = 1
      aggregations {
        alignment_period     = "60s"
        per_series_aligner   = "ALIGN_MEAN"
        cross_series_reducer = "REDUCE_SUM"
      }
    }
  }

  notification_channels = local.notification_channels
  depends_on            = [google_project_service.monitoring]
}
