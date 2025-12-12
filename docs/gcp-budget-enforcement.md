# GCP Budget Enforcement - Automatic Resource Control

This document describes the implementation of automatic resource control when your GCP billing budget reaches 100%. This solution helps prevent unexpected costs by automatically disabling Cloud Run services when budget limits are exceeded.

## Overview

Google Cloud Platform does not provide native "hard caps" on spending - all built-in budget features are notification-only. However, we can implement programmatic spending control using:

1. **Billing Budgets** - Configure budget thresholds with Pub/Sub notifications
2. **Cloud Functions** - Respond to budget alerts and disable services automatically
3. **Cloud Pub/Sub** - Deliver budget notifications to the Cloud Function

## Architecture

```
GCP Billing Budget (100% threshold)
    |
    | (publishes budget alert)
    v
Cloud Pub/Sub Topic
    |
    | (triggers)
    v
Cloud Function (Python)
    |
    | (disables services via API)
    v
Cloud Run Services (stopped)
```

## What Happens When Budget Reaches 100%

1. Billing system detects budget threshold exceeded
2. Pub/Sub notification published to dedicated topic
3. Cloud Function triggered automatically
4. Function authenticates using service account
5. Function stops all Cloud Run services in the project
6. Email notification sent to billing administrators

## Important Limitations

### GCP Billing Limitations

⚠️ **Critical**: There is NO way to create a hard spending cap in GCP. Budget alerts and automated responses have inherent delays:

- **Budget detection delay**: 2-24 hours (billing data is not real-time)
- **Pub/Sub delivery**: Seconds to minutes
- **Function execution**: Seconds
- **Service shutdown**: Immediate for new requests, but:
  - Running requests may complete
  - Other billable services continue
  - Historical billing data may still be processing

💡 **Best Practice**: Set your budget threshold at 80-90% of your true limit to account for delays.

### What Gets Stopped

This implementation stops:
- ✅ Cloud Run services (web and receipt processor)
- ✅ Prevents new Cloud Run invocations

### What Does NOT Get Stopped

- ❌ Cloud Build jobs (if running)
- ❌ Cloud Storage
- ❌ Firestore
- ❌ Artifact Registry
- ❌ Secret Manager
- ❌ Vertex AI API calls
- ❌ Other GCP services

These services continue to accrue charges until manually disabled or until the billing cycle ends.

### Additional Considerations

- **Billing delays**: Costs may continue to accrue for 24-48 hours after services are stopped due to billing data processing delays
- **Partial month charges**: Stopping services mid-month doesn't eliminate fixed costs (storage, etc.)
- **Re-enabling services**: Services must be manually re-enabled after budget is increased or reset
- **Development impact**: Stopping services disrupts all users and deployments

## Setup Instructions

### Prerequisites

1. **Billing account access**: You must have `billing.accounts.update` permission
2. **Project owner role**: Required to set up IAM bindings
3. **APIs enabled**: Cloud Functions, Pub/Sub, Cloud Build

### Quick Setup

Deploy the budget enforcement system using Terraform:

```bash
# Set your billing account ID and budget amount
export BILLING_ACCOUNT_ID=$(gcloud billing accounts list --format="value(name)" --limit=1)
export BUDGET_AMOUNT=50  # Set your monthly budget in USD

# Apply the budget enforcement infrastructure
PROJECT_ID=your-project \
BILLING_ACCOUNT_ID=$BILLING_ACCOUNT_ID \
BUDGET_AMOUNT=$BUDGET_AMOUNT \
./scripts/terraform/apply_budget_enforcement.sh
```

This will:
1. Create a billing budget with 50%, 80%, and 100% thresholds
2. Create a Pub/Sub topic for budget alerts
3. Deploy a Cloud Function that stops Cloud Run services at 100%
4. Set up IAM permissions for the function

### Manual Setup Steps

If you prefer manual configuration:

#### 1. Create Budget with Pub/Sub Notifications

```bash
# Find your billing account ID
gcloud billing accounts list

# Create Pub/Sub topic for budget alerts
gcloud pubsub topics create budget-alerts

# Create budget with programmatic alerts
gcloud billing budgets create \
  --billing-account=YOUR_BILLING_ACCOUNT_ID \
  --display-name="Monthly Budget with Auto-Disable" \
  --budget-amount=50 \
  --threshold-rule=percent=50 \
  --threshold-rule=percent=80 \
  --threshold-rule=percent=100 \
  --notifications-rule-pubsub-topic=projects/YOUR_PROJECT_ID/topics/budget-alerts \
  --notifications-rule-monitoring-notification-channels=EMAIL_CHANNEL_ID
```

#### 2. Deploy Cloud Function

The Cloud Function is located in `infra/functions/budget-enforcer/`. Deploy it:

```bash
cd infra/functions/budget-enforcer

# Deploy the function
gcloud functions deploy budget-enforcer \
  --runtime=python311 \
  --trigger-topic=budget-alerts \
  --entry-point=stop_services \
  --region=us-east1 \
  --service-account=budget-enforcer@YOUR_PROJECT_ID.iam.gserviceaccount.com
```

#### 3. Grant IAM Permissions

The Cloud Function needs permission to stop Cloud Run services:

```bash
# Create service account for the function
gcloud iam service-accounts create budget-enforcer \
  --display-name="Budget Enforcement Function"

# Grant Cloud Run Admin permission
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:budget-enforcer@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/run.admin"

# Grant permission to read Cloud Run services
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:budget-enforcer@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/run.viewer"
```

## Configuration Options

### Budget Thresholds

Edit `infra/terraform/budget-enforcement/main.tf` to customize thresholds:

```hcl
threshold_rules = [
  { percent = 0.5 },  # 50% - Warning
  { percent = 0.8 },  # 80% - Warning
  { percent = 1.0 },  # 100% - Auto-disable services
]
```

### Services to Stop

Edit `infra/functions/budget-enforcer/main.py` to customize which services get stopped:

```python
# Add or remove service names to control which get stopped
SERVICES_TO_STOP = [
    "pklnd-web",
    "pklnd-receipts",
]
```

### Notification Channels

Configure email alerts in the Google Cloud Console:
1. Go to **Monitoring** → **Alerting** → **Notification Channels**
2. Add email addresses for budget alerts
3. Copy the channel ID
4. Add to budget configuration: `--notifications-rule-monitoring-notification-channels=CHANNEL_ID`

## Testing the System

### Test Budget Alert (Without Actual Spending)

You cannot directly trigger a budget alert without actual spending. However, you can test the Cloud Function:

```bash
# Publish a test message to the Pub/Sub topic
gcloud pubsub topics publish budget-alerts \
  --message='{"budgetDisplayName":"Test Budget","alertThresholdExceeded":1.0,"costAmount":100,"budgetAmount":100}'

# Check function logs
gcloud functions logs read budget-enforcer --region=us-east1 --limit=50
```

### Verify Services Are Stopped

```bash
# List Cloud Run services and their status
gcloud run services list --region=us-east1

# Services should show as stopped (0 instances)
```

### Re-enable Services After Testing

```bash
# Re-enable the web service
gcloud run services update pklnd-web \
  --region=us-east1 \
  --min-instances=0 \
  --max-instances=1

# Re-enable the receipt processor
gcloud run services update pklnd-receipts \
  --region=us-east1 \
  --min-instances=0 \
  --max-instances=1
```

## Monitoring and Alerts

### View Budget Status

```bash
# List all budgets
gcloud billing budgets list --billing-account=YOUR_BILLING_ACCOUNT_ID

# View budget details
gcloud billing budgets describe BUDGET_ID --billing-account=YOUR_BILLING_ACCOUNT_ID
```

### View Function Logs

```bash
# View recent function executions
gcloud functions logs read budget-enforcer --region=us-east1 --limit=50

# Follow logs in real-time
gcloud functions logs read budget-enforcer --region=us-east1 --limit=50 --follow
```

### Set Up Additional Alerts

Configure Cloud Monitoring alerts for:
- Cloud Function failures
- Pub/Sub message delivery failures
- Cloud Run service status changes

## Recovery Procedures

### When Services Are Auto-Disabled

1. **Assess the situation**:
   - Check actual spending in Billing Console
   - Determine if budget needs adjustment
   - Identify what caused the overspend

2. **Increase or adjust budget** (if appropriate):
   ```bash
   gcloud billing budgets update BUDGET_ID \
     --billing-account=YOUR_BILLING_ACCOUNT_ID \
     --budget-amount=NEW_AMOUNT
   ```

3. **Re-enable services**:
   ```bash
   # Use the deployment script to re-deploy
   PROJECT_ID=your-project ./scripts/terraform/deploy_services.sh
   
   # Or manually update each service
   gcloud run services update SERVICE_NAME \
     --region=us-east1 \
     --min-instances=0 \
     --max-instances=1
   ```

4. **Verify services are running**:
   ```bash
   gcloud run services list --region=us-east1
   curl https://YOUR_SERVICE_URL/
   ```

### Emergency Disable (Manual)

If you need to immediately stop services manually:

```bash
# Stop all Cloud Run services
for service in $(gcloud run services list --format="value(metadata.name)"); do
  gcloud run services update $service \
    --region=us-east1 \
    --max-instances=0
  echo "Stopped: $service"
done
```

## Cost of This Solution

The budget enforcement system itself has minimal costs:

- **Cloud Function**: 
  - Free tier: 2 million invocations/month
  - After free tier: $0.40 per million invocations
  - Expected cost: $0.00/month (well within free tier)

- **Pub/Sub**:
  - Free tier: 10 GB/month
  - After free tier: $0.06 per GB
  - Expected cost: $0.00/month (minimal data)

- **Cloud Storage** (function code):
  - ~$0.01/month

**Total added cost**: < $0.05/month

## Alternative Approaches

### 1. Budget Alerts Only (No Auto-Disable)

Remove the auto-disable function and rely on email alerts:
- ✅ Simpler setup
- ✅ No risk of accidental service disruption
- ❌ Requires manual intervention
- ❌ Costs can continue to accrue

### 2. Cloud Run Concurrency Limits

Set very low concurrency limits to reduce costs:
```bash
gcloud run services update SERVICE_NAME \
  --concurrency=1 \
  --max-instances=1
```
- ✅ Limits concurrent usage
- ❌ Doesn't stop billing
- ❌ Poor user experience during traffic spikes

### 3. Hybrid Approach (Recommended)

Combine budget alerts with manual monitoring:
- Set budget at 80% of true limit
- Email alerts at 50%, 80%, 90%
- Auto-disable at 100% as last resort
- Regular cost reviews

### 4. GitHub Actions Instead of Cloud Build

For development builds, use GitHub Actions (free tier):
- See [GitHub Actions CI/CD Guide](github-actions-ci-cd-guide.md)
- Free tier: 2,000-3,000 minutes/month
- Keep Cloud Build only for production deployments

## Best Practices

### Budget Setting

1. **Set realistic budgets**: Base on actual usage patterns
2. **Use percentage thresholds**: 50%, 80%, 90%, 100%
3. **Account for growth**: Allow headroom for increased usage
4. **Review monthly**: Adjust based on actual spending

### Cost Monitoring

1. **Check billing daily**: Don't wait for budget alerts
2. **Use cost estimation scripts**: `./scripts/terraform/estimate_build_costs.sh`
3. **Enable detailed billing exports**: Export to BigQuery for analysis
4. **Set up cost anomaly detection**: Use Google Cloud cost alerts

### Service Management

1. **Use least privilege**: Give function minimal required permissions
2. **Test in non-production**: Verify function works before relying on it
3. **Have recovery procedures**: Document how to re-enable services
4. **Monitor function health**: Ensure function is working

### Development Workflow

1. **Use local development**: Test locally before deploying
2. **Batch deployments**: Deploy multiple changes together
3. **Use preview environments**: Reduce production deployments
4. **Optimize build times**: Current setup is already optimized

## Troubleshooting

### Budget Alerts Not Triggering

**Symptoms**: No Pub/Sub messages when budget threshold reached

**Causes**:
- Budget notifications not configured correctly
- Pub/Sub topic doesn't exist or has wrong permissions
- Billing data delayed (wait 24-48 hours)

**Solutions**:
```bash
# Verify Pub/Sub topic exists
gcloud pubsub topics list | grep budget-alerts

# Check budget configuration
gcloud billing budgets list --billing-account=YOUR_BILLING_ACCOUNT_ID

# Verify Pub/Sub permissions
gcloud pubsub topics get-iam-policy budget-alerts
```

### Cloud Function Not Executing

**Symptoms**: Pub/Sub messages received but services not stopped

**Causes**:
- Function has errors
- Insufficient IAM permissions
- Function not subscribed to correct topic

**Solutions**:
```bash
# Check function logs for errors
gcloud functions logs read budget-enforcer --region=us-east1 --limit=50

# Verify function exists and is deployed
gcloud functions list --region=us-east1

# Check function configuration
gcloud functions describe budget-enforcer --region=us-east1

# Test function manually
gcloud pubsub topics publish budget-alerts \
  --message='{"budgetDisplayName":"Test","alertThresholdExceeded":1.0}'
```

### Services Not Stopping

**Symptoms**: Function executes but services remain running

**Causes**:
- Service account lacks permissions
- Wrong service names in function
- API calls failing

**Solutions**:
```bash
# Check service account permissions
gcloud projects get-iam-policy YOUR_PROJECT_ID \
  --flatten="bindings[].members" \
  --filter="bindings.members:serviceAccount:budget-enforcer@*"

# Verify service names
gcloud run services list --format="value(metadata.name)"

# Update function with correct service names
# Edit infra/functions/budget-enforcer/main.py and redeploy
```

### Function Permissions Issues

**Symptoms**: Function logs show permission denied errors

**Solutions**:
```bash
# Grant Cloud Run Admin role
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:budget-enforcer@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/run.admin"

# Grant Service Account User role (required to act as service account)
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:budget-enforcer@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"
```

## Security Considerations

### Service Account Permissions

The budget enforcer service account has powerful permissions (`roles/run.admin`). Best practices:

1. **Use dedicated service account**: Don't reuse other service accounts
2. **Audit regularly**: Review permissions quarterly
3. **Enable Cloud Audit Logs**: Track all admin actions
4. **Restrict function access**: Only budget system should trigger function

### Pub/Sub Topic Security

Secure the budget alerts topic:

```bash
# Restrict publishing to Cloud Billing only
gcloud pubsub topics set-iam-policy budget-alerts policy.yaml
```

Where `policy.yaml`:
```yaml
bindings:
- members:
  - serviceAccount:cloud-billing@google.com
  role: roles/pubsub.publisher
- members:
  - serviceAccount:budget-enforcer@YOUR_PROJECT_ID.iam.gserviceaccount.com
  role: roles/pubsub.subscriber
```

### Function Code Security

- Keep function code in version control
- Review changes before deploying
- Use service account with minimal required permissions
- Enable Cloud Audit Logs for function invocations

## Related Documentation

- [Cloud Build Cost Analysis](cloud-build-cost-analysis.md) - Detailed cost breakdown
- [Cloud Build Cost Summary](cloud-build-cost-summary.md) - Quick reference guide
- [Build Performance Optimizations](build-performance-optimizations.md) - Speed improvements
- [GitHub Actions CI/CD Guide](github-actions-ci-cd-guide.md) - Alternative build system
- [Google Cloud Budgets Documentation](https://cloud.google.com/billing/docs/how-to/budgets)
- [Google Cloud Functions Documentation](https://cloud.google.com/functions/docs)

## Summary

This budget enforcement system provides automatic protection against runaway costs by:

✅ Monitoring billing in near-real-time  
✅ Sending alerts at 50%, 80%, and 100% thresholds  
✅ Automatically stopping Cloud Run services at 100%  
✅ Providing email notifications to administrators  
✅ Minimal cost (< $0.05/month) for the enforcement system itself  

⚠️ **Remember**: This is not a hard cap. Delays in billing data and other non-Cloud Run services mean costs can still exceed your budget. Use this as a safety net, not a replacement for proper cost monitoring and management.

**Recommendation**: Set your budget at 80-90% of your true spending limit to account for delays and continue to monitor costs regularly.
