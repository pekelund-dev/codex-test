# Budget Alert Handling Setup Guide

This guide provides step-by-step instructions to add automatic budget alert handling to your application deployment.

## Overview

The budget alert handling feature prevents runaway GCP costs by:
- Monitoring your monthly budget via GCP Budget Alerts
- Displaying real-time budget status (25%, 50%, 75%, 90%, 100%) in the application header
- Automatically shutting down both Cloud Run services when budget reaches 100%

## Prerequisites

- Existing application deployment (see [terraform-deployment.md](terraform-deployment.md))
- Access to GCP Billing Account
- `gcloud` CLI authenticated with billing permissions

## Step 1: Get Your Billing Account ID

Find your billing account display name:

```bash
gcloud billing accounts list
```

Note the `DISPLAY_NAME` column value - you'll need this in Step 2.

## Step 2: Configure Budget Alert Variables

Add these variables to your infrastructure configuration.

**Recommended: Terraform Variables File**

Create or update `infra/terraform/infrastructure/terraform.tfvars`:

```hcl
# Required variables
project_id = "your-project-id"
enable_budget_alert = true
budget_amount = 1
billing_account_display_name = "Your Billing Account Name"  # From Step 1

# Optional variables (with defaults shown)
# region = "us-east1"
# firestore_database_name = "receipts-db"
# bucket_name = ""  # Auto-generated if empty
```

**Alternative: Environment Variables**

If you prefer to use environment variables:

```bash
export ENABLE_BUDGET_ALERT=true
export BUDGET_AMOUNT=1  # Monthly budget in USD
export BILLING_ACCOUNT_DISPLAY_NAME="Your Billing Account Name"  # From Step 1
```

Note: The deployment script converts env vars to `TF_VAR_*` format automatically.

## Step 3: Apply Infrastructure Changes

Run the infrastructure provisioning script to create the budget alert resources.

**If using terraform.tfvars (recommended):**

```bash
PROJECT_ID=your-project-id ./scripts/terraform/apply_infrastructure.sh
```

**If using environment variables:**

```bash
PROJECT_ID=your-project-id \
ENABLE_BUDGET_ALERT=true \
BUDGET_AMOUNT=1 \
BILLING_ACCOUNT_DISPLAY_NAME="Your Billing Account Name" \
./scripts/terraform/apply_infrastructure.sh
```

This creates:
- Pub/Sub topic `billing-alerts`
- GCP Budget with alert thresholds at 50%, 75%, 90%, 100%
- Service account `cloud-run-pubsub-invoker`
- IAM permissions for Pub/Sub to invoke Cloud Run services

## Step 4: Deploy Application Services

Deploy both Cloud Run services with the budget alert handling code:

```bash
PROJECT_ID=your-project-id ./scripts/terraform/deploy_services.sh
```

The services now include:
- Budget alert endpoint at `/api/billing/alerts`
- Budget percentage tracking
- Visual budget display in application header
- Automatic shutdown at 100% budget threshold

## Step 5: Verify Setup

### Check Infrastructure Resources

Verify the Pub/Sub topic was created:

```bash
gcloud pubsub topics describe billing-alerts --project=your-project-id
```

Verify the budget was created:

```bash
gcloud billing budgets list --billing-account=YOUR_BILLING_ACCOUNT_ID
```

### Check Application Deployment

Visit your application URL and check the red status bar at the top. Initially, no budget percentage will be shown until the first alert is received.

### Test with Manual Alert (Optional)

Send a test budget alert to verify the endpoint works:

```bash
# Create test payload (50% budget)
echo '{"message":{"data":"eyJjb3N0QW1vdW50IjoyNS4wLCJidWRnZXRBbW91bnQiOjUwLjB9"}}' | \
curl -X POST \
  -H "Content-Type: application/json" \
  -d @- \
  https://your-web-service-url/api/billing/alerts
```

The base64 data decodes to: `{"costAmount":25.0,"budgetAmount":50.0}` (50% of budget).

After sending, refresh your application and you should see "Budget: 50%" in the status bar.

## Budget Thresholds

| Threshold | Behavior |
|-----------|----------|
| 50% | Alert received, displayed in status bar |
| 75% | Alert received, displayed in status bar |
| 90% | Alert received, displayed in status bar |
| 100% | **Shutdown triggered** - Services scale to zero |

## Visual Indicator

Once alerts are received, budget status appears in the red status bar:
- **Location**: Top of every page
- **Format**: "Budget: XX%" with wallet icon
- **Updates**: Real-time as alerts arrive from GCP

## Troubleshooting

### Budget Alert Not Appearing

1. Check Pub/Sub topic exists:
   ```bash
   gcloud pubsub topics list --project=your-project-id | grep billing-alerts
   ```

2. Check budget configuration:
   ```bash
   gcloud billing budgets list --billing-account=YOUR_BILLING_ACCOUNT_ID
   ```

3. Check Cloud Run service logs for alert reception:
   ```bash
   gcloud logging read "resource.type=cloud_run_revision AND textPayload:'billing alert'" --limit 10
   ```

### Application Shutdown at 100%

When budget reaches 100%, both services automatically report as unhealthy and scale to zero. To recover:

1. Address the budget issue (increase limit or wait for new billing cycle)
2. Redeploy services:
   ```bash
   PROJECT_ID=your-project-id ./scripts/terraform/deploy_services.sh
   ```

## Updating Budget Amount

To change your monthly budget:

**If using terraform.tfvars (recommended):**

1. Update `infra/terraform/infrastructure/terraform.tfvars`:
   ```hcl
   budget_amount = 5  # New budget amount in USD
   ```

2. Reapply infrastructure:
   ```bash
   PROJECT_ID=your-project-id ./scripts/terraform/apply_infrastructure.sh
   ```

**If using environment variables:**

```bash
# Update and reapply
PROJECT_ID=your-project-id \
BUDGET_AMOUNT=5 \
./scripts/terraform/apply_infrastructure.sh
```

## Disabling Budget Alerts

To disable the feature:

**If using terraform.tfvars:**

1. Update `infra/terraform/infrastructure/terraform.tfvars`:
   ```hcl
   enable_budget_alert = false
   ```

2. Reapply infrastructure:
   ```bash
   PROJECT_ID=your-project-id ./scripts/terraform/apply_infrastructure.sh
   ```

**If using environment variables:**

```bash
# Set to false
export ENABLE_BUDGET_ALERT=false

# Reapply infrastructure
PROJECT_ID=your-project-id \
ENABLE_BUDGET_ALERT=false \
./scripts/terraform/apply_infrastructure.sh
```

## Additional Resources

- [Complete Budget Alert Documentation](billing-alert-shutdown.md) - Full feature details
- [Terraform Deployment Guide](terraform-deployment.md) - General deployment process
- [GCP Budget Alerts Documentation](https://cloud.google.com/billing/docs/how-to/budgets)
