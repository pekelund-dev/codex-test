# Terraform Environment Configurations

This directory contains environment-specific Terraform variable configurations for deploying the pklnd application to different environments.

## Files

- **production.tfvars** - Production environment configuration with standard service names
- **test.tfvars** - Test environment configuration with test-specific service names

## Usage

These files are referenced by deployment scripts:

- `scripts/terraform/deploy_services.sh` - Uses production.tfvars (or inline variables)
- `scripts/terraform/deploy_to_test.sh` - Uses test.tfvars (or inline variables)

The variables defined here override defaults in the main Terraform configuration.

## Environment Isolation

Environments are isolated using:

1. **Different service names** - e.g., `pklnd-web` vs `pklnd-web-test`
2. **Different Firestore databases** - e.g., `receipts-db` vs `receipts-db-test`
3. **Different storage buckets** - e.g., `pklnd-receipts-<project>` vs `pklnd-receipts-test-<project>`
4. **Different secrets** - e.g., `pklnd-app-config` vs `pklnd-app-config-test`
5. **Different Terraform state prefixes** - e.g., `deployment` vs `deployment-test`

This allows both environments to coexist in the same GCP project without interfering with each other.

## Adding New Environments

To add a new environment (e.g., staging):

1. Create `staging.tfvars` with appropriate variable overrides:
   ```hcl
   web_service_name = "pklnd-web-staging"
   receipt_service_name = "pklnd-receipts-staging"
   ```

2. Create deployment scripts:
   - `scripts/terraform/setup_staging_infrastructure.sh`
   - `scripts/terraform/deploy_to_staging.sh`

3. Update GitHub Actions workflows to include the new environment option

4. Document the staging environment in `docs/test-environment-setup.md`

## Related Documentation

- [Test Environment Setup Guide](../../docs/test-environment-setup.md)
- [Test Environment Quick Reference](../../docs/test-environment-quick-reference.md)
- [Terraform Deployment](../../docs/terraform-deployment.md)
