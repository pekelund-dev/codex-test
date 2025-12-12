#!/bin/bash
set -euo pipefail

# Script to deploy budget enforcement infrastructure
# This creates a billing budget, Pub/Sub topic, and Cloud Function to
# automatically stop Cloud Run services when budget reaches 100%

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TERRAFORM_DIR="${REPO_ROOT}/infra/terraform/budget-enforcement"

# Required environment variables
: "${PROJECT_ID:?Environment variable PROJECT_ID must be set}"

# Optional environment variables
REGION="${REGION:-us-east1}"
BILLING_ACCOUNT_ID="${BILLING_ACCOUNT_ID:-}"
BUDGET_AMOUNT="${BUDGET_AMOUNT:-50}"
CREATE_BUDGET="${CREATE_BUDGET:-false}"

echo "======================================"
echo "Budget Enforcement Infrastructure"
echo "======================================"
echo "Project ID: ${PROJECT_ID}"
echo "Region: ${REGION}"
echo "Budget Amount: \$${BUDGET_AMOUNT}/month"
echo "Billing Account: ${BILLING_ACCOUNT_ID:-Not provided}"
echo "Create Budget: ${CREATE_BUDGET}"
echo ""

# Check if gcloud is authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q .; then
    echo "Error: No active gcloud authentication found"
    echo "Run: gcloud auth login"
    exit 1
fi

# Check if project exists
if ! gcloud projects describe "${PROJECT_ID}" &>/dev/null; then
    echo "Error: Project ${PROJECT_ID} not found or not accessible"
    exit 1
fi

# Set gcloud project
gcloud config set project "${PROJECT_ID}" --quiet

echo "Initializing Terraform..."
cd "${TERRAFORM_DIR}"

terraform init

echo ""
echo "Planning infrastructure changes..."
terraform plan \
    -var="project_id=${PROJECT_ID}" \
    -var="region=${REGION}" \
    -var="billing_account_id=${BILLING_ACCOUNT_ID}" \
    -var="budget_amount=${BUDGET_AMOUNT}" \
    -var="create_budget=${CREATE_BUDGET}" \
    -out=tfplan

echo ""
read -p "Apply these changes? (yes/no): " -r
echo
if [[ ! $REPLY =~ ^[Yy]es$ ]]; then
    echo "Aborted."
    exit 1
fi

echo ""
echo "Applying infrastructure changes..."
terraform apply tfplan

echo ""
echo "======================================"
echo "Deployment Complete!"
echo "======================================"
echo ""

# Get outputs
TOPIC_NAME=$(terraform output -raw budget_topic_name 2>/dev/null || echo "budget-alerts")
FUNCTION_NAME=$(terraform output -raw function_name 2>/dev/null || echo "budget-enforcer")

echo "Resources created:"
echo "  ✓ Pub/Sub topic: ${TOPIC_NAME}"
echo "  ✓ Cloud Function: ${FUNCTION_NAME}"
echo "  ✓ Service account: budget-enforcer@${PROJECT_ID}.iam.gserviceaccount.com"
echo ""

if [[ "${CREATE_BUDGET}" == "false" ]] || [[ -z "${BILLING_ACCOUNT_ID}" ]]; then
    echo "⚠️  Budget not created (requires billing account permissions)"
    echo ""
    echo "To create the budget manually, run:"
    echo ""
    
    if [[ -z "${BILLING_ACCOUNT_ID}" ]]; then
        echo "# First, find your billing account ID:"
        echo "gcloud billing accounts list"
        echo ""
        echo "# Then create the budget:"
        echo "BILLING_ACCOUNT_ID=\$(gcloud billing accounts list --format='value(name)' --limit=1)"
    else
        echo "BILLING_ACCOUNT_ID=${BILLING_ACCOUNT_ID}"
    fi
    
    echo "gcloud billing budgets create \\"
    echo "  --billing-account=\${BILLING_ACCOUNT_ID} \\"
    echo "  --display-name='Monthly Budget with Auto-Disable' \\"
    echo "  --budget-amount=${BUDGET_AMOUNT} \\"
    echo "  --threshold-rule=percent=50 \\"
    echo "  --threshold-rule=percent=80 \\"
    echo "  --threshold-rule=percent=100 \\"
    echo "  --notifications-rule-pubsub-topic=projects/${PROJECT_ID}/topics/${TOPIC_NAME}"
    echo ""
fi

echo "Next steps:"
echo ""
echo "1. Test the function:"
echo "   gcloud pubsub topics publish ${TOPIC_NAME} \\"
echo "     --message='{\"budgetDisplayName\":\"Test\",\"alertThresholdExceeded\":1.0,\"costAmount\":100,\"budgetAmount\":100}'"
echo ""
echo "2. Check function logs:"
echo "   gcloud functions logs read ${FUNCTION_NAME} --region=${REGION} --limit=50"
echo ""
echo "3. View Cloud Run services:"
echo "   gcloud run services list --region=${REGION}"
echo ""
echo "For more information, see: docs/gcp-budget-enforcement.md"
echo ""
