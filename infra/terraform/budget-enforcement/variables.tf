variable "project_id" {
  description = "The GCP project ID"
  type        = string
}

variable "region" {
  description = "The GCP region for deploying resources"
  type        = string
  default     = "us-east1"
}

variable "budget_topic_name" {
  description = "Name of the Pub/Sub topic for budget alerts"
  type        = string
  default     = "budget-alerts"
}

variable "function_name" {
  description = "Name of the Cloud Function"
  type        = string
  default     = "budget-enforcer"
}

variable "function_service_account" {
  description = "Service account name for the Cloud Function"
  type        = string
  default     = "budget-enforcer"
}

variable "billing_account_id" {
  description = "The billing account ID (format: XXXXXX-XXXXXX-XXXXXX)"
  type        = string
  default     = ""
}

variable "budget_amount" {
  description = "Monthly budget amount in USD"
  type        = number
  default     = 50
}

variable "budget_display_name" {
  description = "Display name for the budget"
  type        = string
  default     = "Monthly Budget with Auto-Disable"
}

variable "create_budget" {
  description = "Whether to create the billing budget (requires billing account permissions)"
  type        = bool
  default     = false
}

variable "threshold_rules" {
  description = "Budget threshold rules as percentages (0.5 = 50%, 1.0 = 100%)"
  type = list(object({
    percent = number
  }))
  default = [
    { percent = 0.5 },  # 50%
    { percent = 0.8 },  # 80%
    { percent = 1.0 },  # 100%
  ]
}

variable "disable_default_iam_recipients" {
  description = "Disable default IAM recipients for budget alerts"
  type        = bool
  default     = false
}
