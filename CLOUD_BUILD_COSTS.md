# Cloud Build Costs - Investigation Summary

**Date**: 2026-02-17  
**Status**: ⚠️ Cost spike investigation completed

## Investigation Result (Where and When Cloud Build Was Added)

### Where Cloud Build is currently used

Cloud Build usage is wired into deployment scripts:
- `scripts/terraform/deploy_services.sh` (`gcloud builds submit` for web and receipt-parser)
- `scripts/terraform/deploy_to_test.sh` (`gcloud builds submit` for web and receipt-parser)
- `scripts/legacy/deploy_cloud_run.sh` and `scripts/legacy/deploy_receipt_processor.sh`
- Build config files: `cloudbuild.yaml` and `receipt-parser/cloudbuild.yaml`

Cloud Build API is also enabled in infrastructure:
- `infra/terraform/infrastructure/main.tf` (`cloudbuild.googleapis.com`)

### When Cloud Build usage was added

In the available repository history, Cloud Build files and Terraform deploy scripts are present in the earliest reachable commit:
- Commit: `cca8dfc20252e1d0595a04bd027ead505a362f74`
- Date: `2026-02-13 20:04:07 +0100`

Because this clone has limited reachable history (`grafted` base commit), there is no earlier local commit to inspect.

`TICKETS.md` also documents explicit Cloud Build integration under **Ticket 6.1 — Add tests before Cloud Build**.

## Recommended Solution (No Cloud Build)

Use the existing GitHub Actions workflow that already avoids Cloud Build:
- Workflow: `.github/workflows/deploy-cloud-run.yml`
- Build method: `docker/build-push-action` (GitHub runner + Buildx cache)
- Deploy method: `gcloud run deploy`

This removes Cloud Build runtime costs entirely for app deployments and still pushes images to Artifact Registry.

### Practical next step

Use GitHub Actions deployment as the default path and reserve Cloud Build scripts only for fallback/manual operations.

## How Much Does Cloud Build Cost?

| Deployment Frequency | Monthly Cost | Notes |
|---------------------|--------------|-------|
| **10 builds/month** | **~$2** | Light development |
| **30 builds/month** | **~$5** | Active development (typical) |
| **100 builds/month** | **~$16** | Continuous integration |

### Per-Deployment Costs

- **Typical deployment** (warm cache): $0.08 - $0.14
- **First deployment** (cold cache): $0.21 - $0.32

## Current Setup

- **Machine Type**: E2_HIGHCPU_8 (8 vCPUs, 8 GB RAM)
- **Cost Rate**: $0.016 per build-minute
- **Services**: 2 (web + receipt processor)
- **Build Strategy**: Parallel builds
- **Build Time**: 5-12 minutes with caching

## Key Findings

✅ **Already Optimized**: The setup includes:
- BuildKit caching (60-80% faster rebuilds)
- Parallel builds (50% time reduction)
- Fast machines (30-40% faster compilation)
- Auto cleanup (60-75% storage savings)

✅ **Cost-Effective**: At ~$5/month for active development, this saves 5-7.5 hours of developer time per month (cost: $0.67-1.00 per hour saved)

⚠️ **Action Needed**: For cost spike mitigation, move primary deployments to the GitHub Actions path that does not use Cloud Build.

## What's Not Included in Free Tier

⚠️ **Important**: E2_HIGHCPU_8 machines are NOT covered by Google Cloud's free tier (120 minutes/day). All build minutes are billable.

The free tier only applies to slower n1-standard-1 machines, which would take 20-40 minutes per build (vs 5-12 minutes currently).

## Monitoring Your Costs

### 1. Run the Cost Estimation Script

```bash
PROJECT_ID=your-project ./scripts/terraform/estimate_build_costs.sh
```

This analyzes your actual build history and projects monthly costs.

### 2. Set Up Budget Alerts

Recommended thresholds:
- 🟡 Warning at $10/month
- 🟡 Warning at $15/month
- 🔴 Alert at $20/month

### 3. Review Actual Costs

Check your actual costs in the [GCP Billing Console](https://console.cloud.google.com/billing)

## When to Optimize Further

For cost spike mitigation, prioritize these changes:

1. **Use smaller machine for receipt processor**: Test E2_HIGHCPU_4 (saves 30-40%)
2. **Skip unchanged services**: Only rebuild what changed (saves up to 50%)
3. **Use GitHub Actions for deployments**: Avoid Cloud Build runtime charges entirely

## Documentation

For detailed analysis and recommendations, see:

- 📊 **[Cloud Build Cost Analysis](docs/cloud-build-cost-analysis.md)** - Comprehensive breakdown
- 📋 **[Cost Summary](docs/cloud-build-cost-summary.md)** - Quick reference guide
- 🚀 **[Build Performance Optimizations](docs/build-performance-optimizations.md)** - Technical details
- 🔄 **[GitHub Actions CI/CD Guide](docs/github-actions-ci-cd-guide.md)** - Alternative to Cloud Build

## Quick Reference

| Metric | Value |
|--------|-------|
| Machine type | E2_HIGHCPU_8 |
| Cost per build-minute | $0.016 |
| Typical build time | 5-12 minutes |
| Services built | 2 (parallel) |
| Build strategy | Parallel |
| Caching | BuildKit inline |
| Typical deployment cost | $0.08-0.14 |
| Monthly cost (30 builds) | ~$5 |

## Recommendation

✅ **Use GitHub Actions as the default deployment path** to avoid Cloud Build runtime costs.

📊 **Monitor costs** for 1-2 months using the provided tools.

🔔 **Set up budget alerts** at $10, $15, and $20/month thresholds.

---

**Last Updated**: 2026-02-17  
**Next Review**: After migration of default deployment path away from Cloud Build
