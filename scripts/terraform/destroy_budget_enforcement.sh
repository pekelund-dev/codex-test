#!/bin/bash
set -euo pipefail

# Script to destroy budget enforcement infrastructure

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TERRAFORM_DIR="${REPO_ROOT}/infra/terraform/budget-enforcement"

# Required environment variables
: "${PROJECT_ID:?Environment variable PROJECT_ID must be set}"

echo "======================================"
echo "Destroy Budget Enforcement"
echo "======================================"
echo "Project ID: ${PROJECT_ID}"
echo ""
echo "⚠️  WARNING: This will remove:"
echo "  - Cloud Function (budget-enforcer)"
echo "  - Pub/Sub topic and subscription"
echo "  - Service account"
echo "  - Function source bucket"
echo ""
echo "Note: The billing budget must be deleted manually:"
echo "  gcloud billing budgets delete BUDGET_ID --billing-account=YOUR_BILLING_ACCOUNT_ID"
echo ""

read -p "Continue with destruction? (yes/no): " -r
echo
if [[ ! $REPLY =~ ^[Yy]es$ ]]; then
    echo "Aborted."
    exit 1
fi

cd "${TERRAFORM_DIR}"

echo "Destroying infrastructure..."
terraform destroy \
    -var="project_id=${PROJECT_ID}" \
    -auto-approve

echo ""
echo "======================================"
echo "Destruction Complete!"
echo "======================================"
echo ""
echo "Budget enforcement infrastructure has been removed."
echo ""
echo "To delete the budget manually:"
echo "1. List budgets:"
echo "   gcloud billing budgets list --billing-account=YOUR_BILLING_ACCOUNT_ID"
echo ""
echo "2. Delete budget:"
echo "   gcloud billing budgets delete BUDGET_ID --billing-account=YOUR_BILLING_ACCOUNT_ID"
echo ""
