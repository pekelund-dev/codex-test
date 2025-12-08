variable "project_id" {
  description = "Target Google Cloud project id"
  type        = string
}

variable "region" {
  description = "Region for Cloud Run services"
  type        = string
  default     = "europe-north1"
}

variable "bucket_name" {
  description = "Receipt uploads bucket"
  type        = string
}

variable "web_image" {
  description = "Fully qualified container image for the web application"
  type        = string
}

variable "receipt_image" {
  description = "Fully qualified container image for the receipt processor"
  type        = string
}

variable "web_service_account_email" {
  description = "Service account email used by the web application"
  type        = string
}

variable "receipt_service_account_email" {
  description = "Service account email used by the receipt processor"
  type        = string
}

variable "secret_name" {
  description = "Unified Secret Manager secret id that stores application credentials"
  type        = string
  default     = "pklnd-app-config"
}

variable "google_client_id" {
  description = "Google OAuth client id (sourced from the unified secret)"
  type        = string
}

variable "google_client_secret" {
  description = "Google OAuth client secret (sourced from the unified secret)"
  type        = string
  sensitive   = true
}

variable "ai_studio_api_key" {
  description = "Optional Google AI Studio API key stored in the unified secret"
  type        = string
  default     = ""
  sensitive   = true
}

variable "firestore_project_id" {
  description = "Firestore project id used by both services"
  type        = string
  default     = ""
}

variable "gcs_project_id" {
  description = "GCS project id for the receipts bucket"
  type        = string
  default     = ""
}

variable "vertex_ai_project_id" {
  description = "Vertex AI project id for the receipt processor"
  type        = string
  default     = ""
}

variable "vertex_ai_location" {
  description = "Vertex AI location for the receipt processor"
  type        = string
  default     = ""
}

variable "vertex_ai_gemini_model" {
  description = "Gemini model name for the receipt processor"
  type        = string
  default     = "gemini-2.0-flash"
}

variable "logging_project_id" {
  description = "Project id used for structured logging from the receipt processor"
  type        = string
  default     = ""
}

variable "web_service_name" {
  description = "Cloud Run service name for the web application"
  type        = string
  default     = "pklnd-web"
}

variable "receipt_service_name" {
  description = "Cloud Run service name for the receipt processor"
  type        = string
  default     = "pklnd-receipts"
}

variable "allow_unauthenticated_web" {
  description = "Expose the web service publicly"
  type        = bool
  default     = true
}

variable "custom_domain" {
  description = "Optional custom domain to map to the web service"
  type        = string
  default     = ""
}
