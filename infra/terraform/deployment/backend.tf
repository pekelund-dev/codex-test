terraform {
  backend "gcs" {
    # Bucket name and prefix are set dynamically via -backend-config flags
    # during terraform init to support multiple projects and environments.
    # See scripts/terraform/deploy_services.sh for the configuration values:
    #   bucket = "pklnd-terraform-state-<project-id>"
    #   prefix = "deployment"
  }
}
