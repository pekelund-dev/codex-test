variable "project_id" {
  description = "Google Cloud project ID"
  type        = string
}

variable "region" {
  description = "Primary region for Cloud Run, Artifact Registry, and storage"
  type        = string
  default     = "us-central1" # aligns with GCP Always Free usage limits
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

variable "bucket_name" {
  description = "Receipts bucket name for the test environment (defaults to an env-suffixed name)"
  type        = string
  default     = null
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

variable "manage_service_accounts" {
  description = "Whether Terraform should create/manage service accounts. Set to true when the runtime identities do not already exist."
  type        = bool
  default     = false
}

variable "web_service_account_email" {
  description = "Email of an existing web runtime service account (used when manage_service_accounts is false)."
  type        = string
  default     = null
}

variable "receipt_service_account_email" {
  description = "Email of an existing receipt processor service account (used when manage_service_accounts is false)."
  type        = string
  default     = null
}

variable "upload_service_account_email" {
  description = "Email of an existing upload service account (used when manage_service_accounts is false)."
  type        = string
  default     = null
}

# Backwards compatibility: this flag is unused but retained so scripts that
# still pass -var "protect_services=..." do not fail with undeclared variable
# errors. It does not change any resources.
variable "protect_services" {
  description = "Deprecated flag accepted for compatibility; no effect"
  type        = bool
  default     = false
}
