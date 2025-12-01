variable "project_id" {
  description = "Google Cloud project ID"
  type        = string
}

variable "region" {
  description = "Primary region for Cloud Run, Artifact Registry, and storage"
  type        = string
  default     = "europe-north1"
}

variable "env_name" {
  description = "Environment suffix used to keep resources isolated"
  type        = string
  default     = "test"

  validation {
    condition     = var.env_name != "" && lower(var.env_name) != "prod" && lower(var.env_name) != "production"
    error_message = "env_name must be non-empty and must not be the production identifier."
  }
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
