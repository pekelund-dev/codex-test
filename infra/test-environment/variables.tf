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
}
