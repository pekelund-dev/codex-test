# GCP Billing Alert Automatic Shutdown

This document describes the automatic cost control feature that shuts down the application when GCP billing alerts are triggered.

## Overview

To prevent unexpectedly large GCP bills, the application includes an automatic shutdown mechanism that is triggered when monthly budget thresholds are exceeded. When the budget reaches 100%, both Cloud Run services (web and receipt processor) automatically enter a shutdown state and scale to zero instances, completely stopping all cost generation.

## How It Works

1. **Budget Alert Configuration**: A GCP Budget Alert is configured in Terraform to monitor monthly spending
2. **Pub/Sub Integration**: When budget thresholds are exceeded, alerts are published to a Pub/Sub topic
3. **Alert Reception**: Both Cloud Run services receive these alerts via HTTP endpoints
4. **Automatic Shutdown**: When 100% of budget is reached, the application:
   - Enters a shutdown state
   - Reports as unhealthy via health check endpoints
   - Causes Cloud Run to scale to zero instances
   - Stops generating any costs

## Architecture

```
GCP Budget Alert → Pub/Sub Topic → Cloud Run Services
                   (billing-alerts)   ├─ Web Service
                                      └─ Receipt Processor
                                             ↓
                                      Health Check Fails
                                             ↓
                                      Scale to Zero (No Cost)
```

## Budget Thresholds

The budget alert is configured with multiple thresholds:

- **50%** - Warning alert (displayed in status bar)
- **75%** - Warning alert (displayed in status bar)
- **90%** - Warning alert (displayed in status bar)
- **100%** - **SHUTDOWN TRIGGERED** - Application scales to zero

All budget alerts update the status display in the application header, showing the current budget percentage.

## Visual Indicator

When a budget alert is received, the current budget percentage is displayed in the red status bar at the top of the application:

- **Location**: Top of every page, in the application header
- **Display**: "Budget: X%" badge with wallet icon
- **Updates**: Real-time as alerts are received from GCP
- **Visibility**: Shows for all budget levels (25%, 50%, 75%, 90%, 100%)

## Infrastructure Setup

### 1. Enable Budget Alerts

Update your Terraform variables to enable budget alerts:

```bash
# In your terraform.tfvars or environment
enable_budget_alert = true
budget_amount = 1  # Monthly budget in USD
billing_account_display_name = "Your Billing Account Name"
```

### 2. Apply Terraform Infrastructure

```bash
PROJECT_ID=your-project ./scripts/terraform/apply_infrastructure.sh
```

This creates:
- `billing-alerts` Pub/Sub topic
- Budget alert configuration
- Service account for Pub/Sub to invoke Cloud Run
- IAM permissions for alert delivery

### 3. Deploy Services

Deploy both services with the updated configuration:

```bash
PROJECT_ID=your-project ./scripts/terraform/deploy_services.sh
```

## Monitoring and Alerts

### Check Application Status

Both services expose health check endpoints that show the billing shutdown state:

```bash
# Web service
curl https://your-web-service-url/actuator/health

# Receipt processor
curl https://your-receipt-service-url/actuator/health
```

When shutdown is triggered, the health check returns:

```json
{
  "status": "DOWN",
  "components": {
    "billingShutdown": {
      "status": "DOWN",
      "details": {
        "reason": "Budget exceeded: 100.0%",
        "shutdownTimestamp": 1234567890,
        "message": "Application shutdown due to billing alert. Scaling to zero to prevent further costs."
      }
    }
  }
}
```

### View Logs

When shutdown is triggered, both services log prominent error messages:

```
ERROR: BILLING ALERT SHUTDOWN TRIGGERED: Budget exceeded: 100.0%
ERROR: Application will report unhealthy and scale to zero to stop generating costs
ERROR: To re-enable, restart the Cloud Run service or redeploy
```

View logs in Cloud Logging:

```bash
# Web service logs
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=pklnd-web" --limit 50

# Receipt processor logs
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=pklnd-receipts" --limit 50
```

## Re-enabling After Shutdown

Once the budget has been addressed (e.g., new billing period or increased budget), you can re-enable the services:

### Option 1: Redeploy Services

```bash
PROJECT_ID=your-project ./scripts/terraform/deploy_services.sh
```

This deploys fresh containers without the shutdown state.

### Option 2: Increase Budget

If you need to continue operating in the same billing period:

1. Update your budget amount:
   ```bash
   # Update terraform.tfvars
   budget_amount = 100  # Increase budget
   ```

2. Apply infrastructure changes:
   ```bash
   PROJECT_ID=your-project ./scripts/terraform/apply_infrastructure.sh
   ```

3. Redeploy services (as above)

### Option 3: Manual Cloud Run Restart

For emergency restoration, you can deploy new revisions via the Cloud Console or gcloud CLI, though redeployment via scripts is recommended for consistency.

## Testing

### Manual Test with Sample Alert

You can test the shutdown mechanism by sending a sample Pub/Sub message:

```bash
# Create a sample budget alert payload
cat > /tmp/budget-alert.json <<'EOF'
{
  "message": {
    "data": "eyJjb3N0QW1vdW50IjoxMDAuMCwiYnVkZ2V0QW1vdW50Ijo1MC4wfQ==",
    "messageId": "test-message-id",
    "publishTime": "2024-01-01T00:00:00Z"
  },
  "subscription": "projects/test-project/subscriptions/test-sub"
}
EOF

# Send to the web service
curl -X POST \
  -H "Content-Type: application/json" \
  -d @/tmp/budget-alert.json \
  https://your-web-service-url/api/billing/alerts

# Send to the receipt processor
curl -X POST \
  -H "Content-Type: application/json" \
  -d @/tmp/budget-alert.json \
  https://your-receipt-service-url/api/billing/alerts
```

The base64-encoded data in the example decodes to:
```json
{"costAmount":100.0,"budgetAmount":50.0}
```

This represents spending $100 against a $50 budget (200% - will trigger shutdown).

### Verify Health Check Failure

After triggering shutdown, verify that health checks fail:

```bash
# Should return status: DOWN
curl https://your-web-service-url/actuator/health
curl https://your-receipt-service-url/actuator/health
```

### Verify Scale to Zero

Check that Cloud Run has scaled to zero instances:

```bash
gcloud run services describe pklnd-web --region us-east1 --format='get(status.traffic[0].revisionName)'
gcloud run revisions describe <REVISION-NAME> --region us-east1 --format='get(status.conditions)'
```

## Security Considerations

### Endpoint Protection

The `/api/billing/alerts` endpoint:
- Is publicly accessible (required for Pub/Sub push subscriptions)
- Has CSRF protection disabled for this specific endpoint
- Should be invoked only by the Pub/Sub service account
- Does not authenticate the caller (relies on GCP's internal network security)

For production environments, consider:
- Enabling [Pub/Sub push endpoint verification](https://cloud.google.com/pubsub/docs/push#setting_up_for_push_authentication)
- Implementing token-based authentication for the endpoint
- Using VPC Service Controls to restrict network access

### IAM Permissions

The `cloud-run-pubsub-invoker` service account needs:
- `roles/run.invoker` on both Cloud Run services
- `roles/pubsub.publisher` on the billing-alerts topic (granted to GCP billing service)

## Cost Impact

### When Operating Normally
The budget alert feature adds minimal cost:
- Pub/Sub topic: ~$0.40 per 1 million messages (typically just a few alerts per month)
- No additional Cloud Run costs (endpoints are part of existing services)

### When Shutdown Is Triggered
**Zero cost** - all Cloud Run instances are terminated, and no further billing occurs for:
- Cloud Run compute
- Firestore operations
- Cloud Storage operations
- Vertex AI API calls

The only ongoing costs are storage (GCS, Firestore) and static resources (Artifact Registry), which cannot be automatically stopped.

## Limitations

1. **Storage Costs Continue**: Cloud Storage buckets and Firestore data still incur storage costs
2. **Manual Re-enablement**: Requires manual intervention to restart services
3. **Alert Latency**: There may be a delay between exceeding budget and receiving the alert
4. **No Rollback**: Once shutdown, the application remains down until manually redeployed

## Troubleshooting

### Budget Alert Not Triggering

Check that:
1. Budget is properly configured in GCP Console
2. Pub/Sub topic exists: `billing-alerts`
3. Budget alert is publishing to the correct topic
4. Service account has necessary permissions

```bash
# Verify Pub/Sub topic
gcloud pubsub topics describe billing-alerts --project=your-project

# Verify budget
gcloud billing budgets list --billing-account=YOUR_BILLING_ACCOUNT_ID
```

### Endpoints Not Receiving Alerts

Check service logs:
```bash
gcloud logging read "resource.type=cloud_run_revision AND textPayload:'billing alert'" --limit 50
```

Verify IAM permissions:
```bash
gcloud run services get-iam-policy pklnd-web --region=us-east1
gcloud run services get-iam-policy pklnd-receipts --region=us-east1
```

### Services Not Scaling to Zero

1. Verify health check is failing (see "Check Application Status" above)
2. Check Cloud Run configuration allows scale to zero:
   ```bash
   gcloud run services describe pklnd-web --region=us-east1 --format='get(spec.template.spec.containerConcurrency,spec.template.metadata.annotations)'
   ```
3. Review Cloud Run logs for health check failures

## References

- [GCP Budget Alerts](https://cloud.google.com/billing/docs/how-to/budgets)
- [Cloud Run Health Checks](https://cloud.google.com/run/docs/configuring/healthchecks)
- [Pub/Sub Push Subscriptions](https://cloud.google.com/pubsub/docs/push)
- [Spring Boot Actuator Health](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints.health)
