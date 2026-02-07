# Test environment configuration
# This file contains test environment-specific variable overrides.
# Used by scripts/terraform/deploy_to_test.sh

# Override default resource names for test environment
web_service_name = "pklnd-web-test"
receipt_service_name = "pklnd-receipts-test"

# Test environment uses lower resource limits
# (configured via service-specific settings in deployment)
