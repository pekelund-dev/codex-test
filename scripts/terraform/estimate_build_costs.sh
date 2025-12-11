#!/usr/bin/env bash
#
# Estimate Cloud Build costs based on recent build history
#
# This script queries Cloud Build history and estimates costs based on:
# - Build duration
# - Machine type
# - Number of builds
#
# Usage:
#   PROJECT_ID=your-project ./scripts/terraform/estimate_build_costs.sh
#   PROJECT_ID=your-project DAYS=7 ./scripts/terraform/estimate_build_costs.sh
#   PROJECT_ID=your-project MACHINE_TYPE=E2_HIGHCPU_4 ./scripts/terraform/estimate_build_costs.sh
#
# Requirements:
# - gcloud CLI authenticated and configured
# - jq (JSON processor)
# - bc (calculator)
#
# Notes:
# - Works best on Linux systems with GNU date
# - BSD date (macOS) support included but may have edge cases with timezone parsing
# - Estimates are based on successful builds only
# - Actual costs may vary based on partial minutes, failures, and network usage

set -euo pipefail

# Check for required dependencies
if ! command -v jq >/dev/null 2>&1; then
  echo "Error: jq is required but not installed." >&2
  echo "Install with: apt-get install jq (Debian/Ubuntu) or brew install jq (macOS)" >&2
  exit 1
fi

if ! command -v bc >/dev/null 2>&1; then
  echo "Error: bc is required but not installed." >&2
  echo "Install with: apt-get install bc (Debian/Ubuntu) or brew install bc (macOS)" >&2
  exit 1
fi

PROJECT_ID=${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}
DAYS=${DAYS:-30}
MACHINE_TYPE=${MACHINE_TYPE:-E2_HIGHCPU_8}

if [[ -z "${PROJECT_ID}" ]]; then
  echo "Error: PROJECT_ID must be set or configured in gcloud" >&2
  exit 1
fi

# Validate DAYS parameter
if [[ ! "${DAYS}" =~ ^[0-9]+$ ]] || [[ "${DAYS}" -le 0 ]]; then
  echo "Error: DAYS must be a positive integer (got: ${DAYS})" >&2
  exit 1
fi

# Pricing per build-minute by machine type (USD)
declare -A MACHINE_PRICING=(
  ["N1_STANDARD_1"]=0.003
  ["E2_STANDARD_2"]=0.005
  ["E2_STANDARD_4"]=0.010
  ["E2_HIGHCPU_4"]=0.010
  ["E2_HIGHCPU_8"]=0.016
  ["E2_STANDARD_8"]=0.020
  ["E2_HIGHCPU_32"]=0.064
  ["N1_HIGHCPU_32"]=0.080
)

PRICE_PER_MINUTE=${MACHINE_PRICING[$MACHINE_TYPE]:-0.016}

echo "=========================================="
echo "Cloud Build Cost Estimation"
echo "=========================================="
echo "Project: ${PROJECT_ID}"
echo "Period: Last ${DAYS} days"
echo "Machine Type: ${MACHINE_TYPE}"
echo "Price per build-minute: \$${PRICE_PER_MINUTE}"
echo ""

# Calculate date filter
START_DATE=$(date -u -d "${DAYS} days ago" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v-${DAYS}d +"%Y-%m-%dT%H:%M:%SZ")

echo "Fetching build history since ${START_DATE}..."

# Fetch build history
BUILDS_JSON=$(gcloud builds list \
  --project="${PROJECT_ID}" \
  --filter="createTime>='${START_DATE}' AND status='SUCCESS'" \
  --format=json \
  --limit=1000 2>/dev/null || echo "[]")

if [[ "${BUILDS_JSON}" == "[]" ]] || [[ -z "${BUILDS_JSON}" ]]; then
  echo ""
  echo "No successful builds found in the last ${DAYS} days."
  echo ""
  echo "Possible reasons:"
  echo "  - No builds have been run recently"
  echo "  - All recent builds failed"
  echo "  - Incorrect project ID"
  exit 0
fi

# Parse build data
BUILD_COUNT=$(echo "${BUILDS_JSON}" | jq 'length')
TOTAL_SECONDS=0

echo "Processing ${BUILD_COUNT} builds..."

# Calculate total build time
while IFS= read -r build; do
  # Extract start and finish times
  start_time=$(echo "${build}" | jq -r '.startTime // empty')
  finish_time=$(echo "${build}" | jq -r '.finishTime // empty')
  
  if [[ -n "${start_time}" ]] && [[ -n "${finish_time}" ]]; then
    # Convert to seconds since epoch
    if date --version >/dev/null 2>&1; then
      # GNU date
      start_epoch=$(date -d "${start_time}" +%s)
      finish_epoch=$(date -d "${finish_time}" +%s)
    else
      # BSD date (macOS)
      start_epoch=$(date -j -f "%Y-%m-%dT%H:%M:%S" "${start_time%.*}" +%s)
      finish_epoch=$(date -j -f "%Y-%m-%dT%H:%M:%S" "${finish_time%.*}" +%s)
    fi
    
    duration=$((finish_epoch - start_epoch))
    TOTAL_SECONDS=$((TOTAL_SECONDS + duration))
  fi
done < <(echo "${BUILDS_JSON}" | jq -c '.[]')

# Verify we have valid build data to calculate with
if [[ ${BUILD_COUNT} -eq 0 ]]; then
  echo ""
  echo "No builds found to calculate costs."
  echo "This might mean:"
  echo "  - No builds have been run in the specified time period"
  echo "  - All builds failed or were cancelled"
  echo "  - The time filter excluded all builds"
  exit 0
fi

if [[ ${TOTAL_SECONDS} -eq 0 ]]; then
  echo ""
  echo "Build timing data is incomplete or missing."
  echo "Found ${BUILD_COUNT} build(s) but no duration information."
  echo "Cannot calculate costs without build duration."
  exit 0
fi

# Calculate costs
TOTAL_MINUTES=$((TOTAL_SECONDS / 60))
if [[ $((TOTAL_SECONDS % 60)) -gt 0 ]]; then
  TOTAL_MINUTES=$((TOTAL_MINUTES + 1))  # Round up partial minutes
fi

TOTAL_COST=$(echo "scale=2; ${TOTAL_MINUTES} * ${PRICE_PER_MINUTE}" | bc)

# Calculate averages
AVG_MINUTES=$((TOTAL_MINUTES / BUILD_COUNT))
AVG_COST=$(echo "scale=2; ${TOTAL_COST} / ${BUILD_COUNT}" | bc)

# Project monthly cost
BUILDS_PER_DAY=$(echo "scale=2; ${BUILD_COUNT} / ${DAYS}" | bc)
PROJECTED_MONTHLY_BUILDS=$(echo "scale=0; ${BUILDS_PER_DAY} * 30" | bc)
PROJECTED_MONTHLY_COST=$(echo "scale=2; ${AVG_COST} * ${PROJECTED_MONTHLY_BUILDS}" | bc)

# Display results
echo ""
echo "=========================================="
echo "Results"
echo "=========================================="
echo ""
echo "Build Statistics:"
echo "  Total builds:          ${BUILD_COUNT}"
echo "  Total build time:      ${TOTAL_MINUTES} minutes ($(echo "scale=1; ${TOTAL_MINUTES}/60" | bc) hours)"
echo "  Average build time:    ${AVG_MINUTES} minutes"
echo ""
echo "Cost Analysis (Last ${DAYS} days):"
echo "  Total cost:            \$${TOTAL_COST}"
echo "  Average cost/build:    \$${AVG_COST}"
echo "  Builds per day:        ${BUILDS_PER_DAY}"
echo ""
echo "Monthly Projection:"
echo "  Estimated builds/month: ${PROJECTED_MONTHLY_BUILDS}"
echo "  Estimated monthly cost: \$${PROJECTED_MONTHLY_COST}"
echo ""

# Add storage cost estimate
ARTIFACT_STORAGE_COST=0.21
TOTAL_MONTHLY_ESTIMATE=$(echo "scale=2; ${PROJECTED_MONTHLY_COST} + ${ARTIFACT_STORAGE_COST}" | bc)

echo "Additional Monthly Costs (Estimated):"
echo "  Artifact Registry:      \$${ARTIFACT_STORAGE_COST} (6 images @ ~350MB each)"
echo ""
echo "Total Estimated Monthly Cost: \$${TOTAL_MONTHLY_ESTIMATE}"
echo ""

# Cost per scenario
echo "=========================================="
echo "Cost Comparison with Documentation"
echo "=========================================="
echo ""
echo "Documentation Estimates (for ${MACHINE_TYPE}):"
echo "  Light development (10 builds/month):   \$1.80"
echo "  Active development (30 builds/month):  \$4.95"
echo "  Continuous integration (100 builds/month): \$16.00"
echo ""
echo "Your Projected Cost: \$${TOTAL_MONTHLY_ESTIMATE} for ${PROJECTED_MONTHLY_BUILDS} builds/month"
echo ""

# Recommendations
if (( $(echo "${TOTAL_MONTHLY_ESTIMATE} < 10" | bc -l) )); then
  echo "✅ Your costs are well within budget. No action needed."
elif (( $(echo "${TOTAL_MONTHLY_ESTIMATE} < 20" | bc -l) )); then
  echo "🟡 Costs are moderate. Monitor trends and consider optimizations if increasing."
else
  echo "🔴 Costs are higher than expected. Consider these optimizations:"
  echo "   1. Implement conditional builds (skip unchanged services)"
  echo "   2. Test smaller machine types (E2_HIGHCPU_4 for receipt processor)"
  echo "   3. Review build triggers and frequency"
  echo ""
  echo "   See docs/cloud-build-cost-analysis.md for detailed recommendations."
fi

echo ""
echo "=========================================="
echo "Next Steps"
echo "=========================================="
echo ""
echo "1. Set up billing budget alerts:"
echo "   - Warning at \$10/month"
echo "   - Warning at \$15/month"
echo "   - Alert at \$20/month"
echo ""
echo "2. Review actual costs in GCP Console:"
echo "   https://console.cloud.google.com/billing"
echo ""
echo "3. For detailed cost analysis, see:"
echo "   - docs/cloud-build-cost-analysis.md"
echo "   - docs/cloud-build-cost-summary.md"
echo ""
