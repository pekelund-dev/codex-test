# Budget Enforcer Cloud Function

This Cloud Function automatically stops Cloud Run services when the GCP budget reaches 100%.

## How It Works

1. GCP Billing Budget sends alert to Pub/Sub topic when threshold is reached
2. Pub/Sub triggers this Cloud Function
3. Function decodes the budget alert message
4. If threshold >= 100%, function stops configured Cloud Run services
5. Function logs all actions for audit trail

## Configuration

### Service Names

Edit `main.py` to customize which services get stopped:

```python
SERVICES_TO_STOP = [
    'pklnd-web',
    'pklnd-receipts',
]
```

### Stop Threshold

Edit `main.py` to change when services get stopped:

```python
STOP_THRESHOLD = 1.0  # 1.0 = 100%, 0.9 = 90%, etc.
```

## Local Testing

You can test the function locally:

```bash
# Install dependencies
pip install -r requirements.txt

# Set environment variables
export PROJECT_ID=your-project-id
export REGION=us-east1

# Test with Python
python -c "
from main import stop_services
import base64
import json

# Simulate a budget alert at 100%
alert = {
    'budgetDisplayName': 'Test Budget',
    'alertThresholdExceeded': 1.0,
    'costAmount': 100.0,
    'budgetAmount': 100.0
}

event = {
    'data': base64.b64encode(json.dumps(alert).encode()).decode()
}

stop_services(event, None)
"
```

## Deployment

Deploy using Terraform (recommended):

```bash
cd /path/to/repo
PROJECT_ID=your-project ./scripts/terraform/apply_budget_enforcement.sh
```

Or deploy manually:

```bash
gcloud functions deploy budget-enforcer \
  --runtime=python311 \
  --trigger-topic=budget-alerts \
  --entry-point=stop_services \
  --region=us-east1 \
  --service-account=budget-enforcer@YOUR_PROJECT_ID.iam.gserviceaccount.com \
  --set-env-vars=PROJECT_ID=YOUR_PROJECT_ID,REGION=us-east1
```

## Monitoring

View function logs:

```bash
gcloud functions logs read budget-enforcer --region=us-east1 --limit=50
```

Test the function:

```bash
gcloud pubsub topics publish budget-alerts \
  --message='{"budgetDisplayName":"Test Budget","alertThresholdExceeded":1.0,"costAmount":100,"budgetAmount":100}'
```

## Permissions Required

The function's service account needs:

- `roles/run.admin` - To stop Cloud Run services
- `roles/run.viewer` - To list Cloud Run services
- `roles/iam.serviceAccountUser` - To act as the service account

These are granted automatically by the Terraform configuration.

## Security Notes

- Function only stops services, never starts or modifies them otherwise
- All actions are logged to Cloud Logging for audit trail
- Service account uses least-privilege permissions
- Pub/Sub topic should only be writable by Cloud Billing

## Troubleshooting

### Services Not Stopping

1. Check function logs for errors:
   ```bash
   gcloud functions logs read budget-enforcer --region=us-east1 --limit=50
   ```

2. Verify service account has permissions:
   ```bash
   gcloud projects get-iam-policy YOUR_PROJECT_ID \
     --flatten="bindings[].members" \
     --filter="bindings.members:serviceAccount:budget-enforcer@*"
   ```

3. Verify service names are correct:
   ```bash
   gcloud run services list --region=us-east1
   ```

### Permission Denied Errors

Grant required permissions:

```bash
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:budget-enforcer@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/run.admin"
```

### Function Not Triggered

1. Verify Pub/Sub topic exists:
   ```bash
   gcloud pubsub topics list | grep budget-alerts
   ```

2. Check function is subscribed to topic:
   ```bash
   gcloud functions describe budget-enforcer --region=us-east1
   ```

3. Test with manual publish (see Monitoring section above)

## Related Documentation

See [docs/gcp-budget-enforcement.md](../../../docs/gcp-budget-enforcement.md) for complete setup and usage guide.
