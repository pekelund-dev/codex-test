variable "project_id" {
  description = "Google Cloud project ID"
  type        = string
}

variable "region" {
  description = "Primary region for Cloud Run, Artifact Registry, and storage"
  type        = string
  default     = "europe-north1"
}

variable "bucket_name" {
  description = "Receipts bucket name for production"
  type        = string
  default     = "pklnd-receipts"
}

variable "web_repository_id" {
  description = "Artifact Registry repository for the web app"
  type        = string
  default     = "web"
}

variable "receipts_repository_id" {
  description = "Artifact Registry repository for the receipt processor"
  type        = string
  default     = "receipts"
}

variable "web_service_account" {
  description = "Service account ID for the web runtime"
  type        = string
  default     = "cloud-run-runtime"
}

variable "receipt_service_account" {
  description = "Service account ID for the receipt processor runtime"
  type        = string
  default     = "receipt-processor"
}

variable "upload_service_account" {
  description = "Service account ID for direct uploads"
  type        = string
  default     = "receipt-uploads"
}

variable "web_service_name" {
  description = "Cloud Run service name for the web app"
  type        = string
  default     = "pklnd-web"
}

variable "receipt_service_name" {
  description = "Cloud Run service name for the receipt processor"
  type        = string
  default     = "pklnd-receipts"
}

variable "config_secret_id" {
  description = "Secret Manager ID holding production configuration payloads"
  type        = string
  default     = "pklnd-config"
}

variable "web_image" {
  description = "Container image to use for the web Cloud Run service (updated later by deploy scripts)"
  type        = string
  default     = "gcr.io/cloudrun/hello"
}

variable "receipt_image" {
  description = "Container image to use for the receipt processor Cloud Run service (updated later by deploy scripts)"
  type        = string
  default     = "gcr.io/cloudrun/hello"
}

variable "allow_web_unauthenticated" {
  description = "Whether unauthenticated users can reach the web service"
  type        = bool
  default     = true
}

variable "allow_receipt_unauthenticated" {
  description = "Whether unauthenticated users can reach the receipt processor"
  type        = bool
  default     = false
}

# Backwards compatibility: accepted but unused. Terraform previously exposed a
# protect_services flag, and some automation still passes it. Keeping the
# variable declared prevents "value for undeclared variable" errors without
# altering the resource graph.
variable "protect_services" {
  description = "Deprecated flag retained for compatibility; no effect on resources"
  type        = bool
  default     = false
}
