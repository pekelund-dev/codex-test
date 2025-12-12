terraform {
  backend "gcs" {
    # Bucket name will be set via -backend-config flag or environment variable
    # Format: pklnd-terraform-state-<project-id>
    # prefix = "deployment"
  }
}
