# GCP Budget Enforcement - Quick Start Guide

This is a streamlined guide to set up automatic resource control when your GCP budget reaches 100%. For complete details, see [GCP Budget Enforcement](gcp-budget-enforcement.md).

## What This Does

When your monthly GCP spending reaches 100% of your budget:
1. 🔔 You receive an email alert
2. 🤖 A Cloud Function automatically stops your Cloud Run services
3. 💰 Prevents additional Cloud Run charges (new requests)

⚠️ **Important**: This is NOT a hard cap. Billing delays mean costs can still exceed your budget. Other GCP services (Storage, Firestore, etc.) continue running.

## Prerequisites

- GCP project with billing enabled
- `gcloud` CLI installed and authenticated
- Terraform installed (optional, recommended)
- Owner or Editor role on the project

## Quick Setup (5 minutes)

### Step 1: Find Your Billing Account ID

```bash
gcloud billing accounts list
```

Copy the `ACCOUNT_ID` value (format: XXXXXX-XXXXXX-XXXXXX).

### Step 2: Deploy Budget Enforcement

```bash
# Set your project and budget
export PROJECT_ID=your-project-id
export BILLING_ACCOUNT_ID=your-billing-account-id
export BUDGET_AMOUNT=50  # Set your monthly budget in USD

# Deploy using Terraform
cd /path/to/codex-test
./scripts/terraform/apply_budget_enforcement.sh
```

The script will:
- ✅ Create a Pub/Sub topic for budget alerts
- ✅ Deploy a Cloud Function to stop services
- ✅ Set up IAM permissions
- ⚠️ Show you the command to create the budget (requires billing permissions)

### Step 3: Create the Budget

If you have billing account permissions, run the command shown by the script:

```bash
gcloud billing budgets create \
  --billing-account=$BILLING_ACCOUNT_ID \
  --display-name="Monthly Budget with Auto-Disable" \
  --budget-amount=$BUDGET_AMOUNT \
  --threshold-rule=percent=50 \
  --threshold-rule=percent=80 \
  --threshold-rule=percent=100 \
  --notifications-rule-pubsub-topic=projects/$PROJECT_ID/topics/budget-alerts
```

If you don't have billing permissions, share this command with your billing administrator.

## Test the Setup

Test that the function works (without actually stopping services in production):

```bash
# Publish a test message (this will actually stop services!)
# Only run this in a test environment
gcloud pubsub topics publish budget-alerts \
  --message='{"budgetDisplayName":"Test Budget","alertThresholdExceeded":0.5,"costAmount":50,"budgetAmount":100}'

# Check the function logs
gcloud functions logs read budget-enforcer --region=us-east1 --limit=50
```

The function should log that it received the alert but didn't stop services (since threshold was 50%, not 100%).

## What Happens When Budget Reaches 100%

1. **Billing system detects**: Your spending reached 100% of budget
2. **Pub/Sub notification**: Alert sent to `budget-alerts` topic
3. **Function triggers**: Cloud Function processes the alert
4. **Services stop**: Both `pklnd-web` and `pklnd-receipts` services are stopped
5. **Email sent**: Billing administrators receive email notification

## Re-enable Services After Budget Reset

Once you've addressed the budget situation (e.g., increased budget or new billing cycle):

```bash
# Re-deploy services
cd /path/to/codex-test
PROJECT_ID=your-project ./scripts/terraform/deploy_services.sh
```

Or manually:

```bash
gcloud run services update pklnd-web \
  --region=us-east1 \
  --min-instances=0 \
  --max-instances=1

gcloud run services update pklnd-receipts \
  --region=us-east1 \
  --min-instances=0 \
  --max-instances=1
```

## Configuration

### Change Budget Thresholds

Edit the budget to alert at different percentages:

```bash
gcloud billing budgets update BUDGET_ID \
  --billing-account=$BILLING_ACCOUNT_ID \
  --threshold-rule=percent=50 \
  --threshold-rule=percent=80 \
  --threshold-rule=percent=90 \
  --threshold-rule=percent=100
```

### Change Which Services Get Stopped

Edit `infra/functions/budget-enforcer/main.py`:

```python
SERVICES_TO_STOP = [
    'pklnd-web',
    'pklnd-receipts',
    # Add more services here
]
```

Then redeploy:

```bash
./scripts/terraform/apply_budget_enforcement.sh
```

### Change Stop Threshold

To stop services at 90% instead of 100%, edit `infra/functions/budget-enforcer/main.py`:

```python
STOP_THRESHOLD = 0.9  # 0.9 = 90%, 1.0 = 100%
```

Then redeploy.

## Monitoring

### View Current Budget Status

```bash
gcloud billing budgets list --billing-account=$BILLING_ACCOUNT_ID
```

### Check Function Logs

```bash
gcloud functions logs read budget-enforcer --region=us-east1 --limit=50
```

### View Cloud Run Service Status

```bash
gcloud run services list --region=us-east1
```

## Troubleshooting

### Budget Not Created

**Error**: Permission denied creating budget

**Solution**: You need `billing.budgets.create` permission. Ask your billing administrator to create the budget using the command from Step 3.

### Function Not Stopping Services

**Check function logs**:
```bash
gcloud functions logs read budget-enforcer --region=us-east1 --limit=50
```

**Common causes**:
1. Service names don't match (check with `gcloud run services list`)
2. Permission denied (function service account needs `roles/run.admin`)
3. Function not triggered (check Pub/Sub topic exists)

### Services Already Stopped

If services are stopped and you want to restart them, use the re-enable commands in the section above.

## Cost of This Solution

The budget enforcement system costs **< $0.05/month**:
- Cloud Function: Free tier (well within 2M invocations/month)
- Pub/Sub: Free tier (minimal data)
- Storage: ~$0.01/month (function code)

## Remove Budget Enforcement

To remove the budget enforcement system:

```bash
export PROJECT_ID=your-project-id
./scripts/terraform/destroy_budget_enforcement.sh
```

This removes the Cloud Function, Pub/Sub topic, and service account. You'll need to manually delete the budget:

```bash
# List budgets
gcloud billing budgets list --billing-account=$BILLING_ACCOUNT_ID

# Delete budget
gcloud billing budgets delete BUDGET_ID --billing-account=$BILLING_ACCOUNT_ID
```

## Best Practices

1. **Set budget at 80-90% of true limit**: Account for billing delays
2. **Monitor costs daily**: Don't rely solely on budget alerts
3. **Test in non-production first**: Verify function works before relying on it
4. **Document recovery procedures**: Know how to re-enable services
5. **Review monthly**: Adjust budget based on actual usage

## Next Steps

- Read the [full documentation](gcp-budget-enforcement.md) for detailed information
- Set up [additional monitoring alerts](https://cloud.google.com/billing/docs/how-to/budgets)
- Review [cost optimization strategies](cloud-build-cost-analysis.md)
- Consider [GitHub Actions for development builds](github-actions-ci-cd-guide.md)

## Get Help

- **Full documentation**: [GCP Budget Enforcement](gcp-budget-enforcement.md)
- **Cost analysis**: [Cloud Build Cost Analysis](cloud-build-cost-analysis.md)
- **GCP documentation**: [Google Cloud Budgets](https://cloud.google.com/billing/docs/how-to/budgets)

---

**Remember**: This is a safety net, not a replacement for cost monitoring. Set realistic budgets and monitor spending regularly!
