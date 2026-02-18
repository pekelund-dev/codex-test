variable "project_id" {
  description = "Target Google Cloud project id"
  type        = string
}

variable "region" {
  description = "Primary region for regional resources"
  type        = string
  default     = "us-east1"
}

variable "firestore_location" {
  description = "Location for the Firestore database"
  type        = string
  default     = "us-east1"
}

variable "firestore_database_name" {
  description = "Firestore database name for the project (defaults to the dedicated receipts-db database)"
  type        = string
  default     = "receipts-db"
}

variable "bucket_name" {
  description = "Name of the receipts bucket; defaults to pklnd-receipts-<project>"
  type        = string
  default     = ""
}

variable "web_artifact_repo" {
  description = "Artifact Registry repository name for the web service"
  type        = string
  default     = "web"
}

variable "receipt_artifact_repo" {
  description = "Artifact Registry repository name for the receipt processor"
  type        = string
  default     = "receipts"
}

variable "web_service_account" {
  description = "Service account id (without domain) for the web Cloud Run service"
  type        = string
  default     = "cloud-run-runtime"
}

variable "receipt_service_account" {
  description = "Service account id (without domain) for the receipt processor Cloud Run service"
  type        = string
  default     = "receipt-processor"
}

variable "app_secret_name" {
  description = "Secret Manager id that stores all application credentials"
  type        = string
  default     = "pklnd-app-config"
}

variable "app_secret_json" {
  description = "Optional JSON payload to seed the unified Secret Manager secret"
  type        = string
  default     = ""
  sensitive   = true
}

variable "enable_budget_alert" {
  description = "Enable budget alerts and automatic shutdown on budget exceeded"
  type        = bool
  default     = false
}

variable "budget_amount" {
  description = "Monthly budget amount in USD"
  type        = number
  default     = 1
}

variable "billing_account_display_name" {
  description = "Display name of the billing account to use for budget alerts (used when billing_account_id is not set)"
  type        = string
  default     = ""
}

variable "billing_account_id" {
  description = "ID of the billing account to use for budget alerts (preferred over billing_account_display_name)"
  type        = string
  default     = ""
}
