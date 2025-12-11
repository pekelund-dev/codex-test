# Cloud Build Cost Summary - Quick Reference

**Last Updated**: December 2024  
**Full Analysis**: See [cloud-build-cost-analysis.md](cloud-build-cost-analysis.md)

## TL;DR - How Much Does It Cost?

| Usage Pattern | Deployments/Month | Monthly Cost | Cost/Deployment |
|---------------|-------------------|--------------|-----------------|
| 🧪 **Light Development** | 10 | **$1.80** | $0.18 |
| 🔨 **Active Development** | 30 | **$4.95** | $0.17 |
| 🚀 **Continuous Integration** | 100 | **$16.00** | $0.16 |

**Bottom Line**: For typical active development (30 deployments/month), expect to pay **~$5/month**.

## What Are We Paying For?

### Cloud Build Configuration

- **Machine Type**: E2_HIGHCPU_8 (8 vCPUs, 8 GB RAM)
- **Cost**: $0.016 per build-minute
- **Services**: 2 (web + receipt processor, built in parallel)
- **Build Time**: 5-12 minutes per deployment (with caching)

### Cost Breakdown per Deployment

```
Warm Cache Build (typical):
├── Web service build:        3-5 min × $0.016/min = $0.05-0.08
├── Receipt processor build:  2-4 min × $0.016/min = $0.03-0.06
└── Total:                    $0.08-0.14

Cold Cache Build (first build or cache miss):
├── Web service build:        8-12 min × $0.016/min = $0.13-0.19
├── Receipt processor build:  5-8 min × $0.016/min = $0.08-0.13
└── Total:                    $0.21-0.32
```

### Additional Monthly Costs

- **Artifact Registry storage**: ~$0.21/month (6 container images)
- **Cloud Storage**: ~$0.00 (build archives auto-deleted)
- **Network egress**: ~$0.00 (within GCP)

## Is This Expensive?

### Cost Context

**Time saved per deployment**: ~10-15 minutes vs slower machines  
**Monthly time saved** (30 deployments): 5-7.5 hours  
**Cost per hour saved**: $0.67-1.00/hour

**Verdict**: ✅ Excellent value for developer time

### Comparison with Alternatives

| Option | Cost/Deploy | Build Time | Verdict |
|--------|-------------|------------|---------|
| **Current (E2_HIGHCPU_8)** | **$0.08-0.32** | **5-12 min** | ✅ **Best balance** |
| Default (n1-standard-1) | $0.03-0.12* | 20-40 min | ❌ Too slow |
| E2_STANDARD_4 | $0.10-0.18 | 10-18 min | 🟡 Possible middle ground |
| Local builds | $0.00 | 15-30 min | ❌ Slow, inconsistent |

*After exhausting 120 free minutes/day (does not apply to E2_HIGHCPU_8)

## Already Optimized! 🎉

The current setup has excellent cost optimizations:

- ✅ **BuildKit caching**: 60-80% rebuild time reduction
- ✅ **Parallel builds**: 50% wall-clock time reduction
- ✅ **Fast machines**: 30-40% compilation time reduction
- ✅ **Small build context**: 60-80% upload time reduction
- ✅ **Auto cleanup**: Only keeps 3 recent images

**Estimated savings**: 40-60% vs non-optimized setup

## Do We Need to Optimize Further?

### Current Answer: **NO** 🎯

The setup is already highly optimized and cost-effective. However, here are options if costs become a concern:

### If Monthly Cost Exceeds $20

1. **Use smaller machine for receipt processor**
   - Change from E2_HIGHCPU_8 to E2_HIGHCPU_4
   - Potential savings: 30-40% on receipt builds
   - Risk: Slower builds

2. **Skip builds for unchanged services**
   - Only rebuild what changed
   - Potential savings: Up to 50%
   - Requires: Git diff logic in deploy script

3. **Use GitHub Actions for PR builds**
   - Free tier: 2,000-3,000 minutes/month
   - Keep Cloud Build for production only
   - Savings: Variable, but could be significant

## Recommended Actions

### Immediate (This Week)

- [x] ✅ Document current costs (this document)
- [ ] 🔔 Set up billing budget alerts:
  - Warning at $10/month
  - Warning at $15/month
  - Alert at $20/month

### Monitor (Next 1-2 Months)

- [ ] 📊 Track actual deployment frequency
- [ ] 💰 Review actual monthly costs
- [ ] 📈 Compare against estimates in this document

### If Optimization Needed (Only if Cost > $20/month)

- [ ] 🧪 Test E2_HIGHCPU_4 for receipt processor
- [ ] 🔀 Implement conditional builds for changed services
- [ ] 🤖 Evaluate GitHub Actions for non-production builds

## Quick Cost Calculator

**Formula**: `(Web build minutes + Receipt build minutes) × $0.016`

**Example calculations**:

```
# Warm cache deployment (typical)
(4 + 3) × $0.016 = $0.11

# Cold cache deployment  
(10 + 6) × $0.016 = $0.26

# 30 deployments/month (3 cold + 27 warm)
(3 × $0.26) + (27 × $0.11) = $3.75 build costs
+ $0.21 storage = ~$4.00/month
```

## Common Questions

### Q: Why not use the free tier?
**A**: The free tier (120 minutes/day) only applies to n1-standard-1 machines, which are too slow. E2_HIGHCPU_8 machines are not covered by free tier.

### Q: Should we reduce the machine type to save money?
**A**: Not recommended. The faster machine actually reduces total cost by completing builds faster. Developer time saved is worth the cost.

### Q: What if we deploy 100 times per month?
**A**: At 100 deployments/month, estimated cost is ~$16/month. Still reasonable for high-velocity development.

### Q: Can we reduce costs without impacting build time?
**A**: Best option is conditional builds (skip unchanged services). This can save up to 50% with no time impact.

### Q: Should we switch to local builds to save money?
**A**: No. Local builds are slower, inconsistent across developers, and lack caching benefits. The $5/month cost is worth it.

### Q: What about GitHub Actions instead of Cloud Build?
**A**: GitHub Actions has a generous free tier (2,000-3,000 minutes/month) and could reduce costs to $0 for typical usage. However, builds are 60-100% slower. Recommended: Use GitHub Actions for PR validation (free) and keep Cloud Build for deployments (fast). See [GitHub Actions CI/CD Guide](github-actions-ci-cd-guide.md) for details.

## Need More Details?

See the comprehensive [Cloud Build Cost Analysis](cloud-build-cost-analysis.md) document for:
- Detailed pricing breakdowns
- Line-by-line configuration analysis
- Alternative build strategy comparisons
- Step-by-step optimization instructions
- Long-term cost projections

For GitHub Actions migration information, see:
- [GitHub Actions CI/CD Guide](github-actions-ci-cd-guide.md) - Complete guide for GitHub Actions setup
- Includes cost comparison, implementation guide, and workflow examples
- Recommended hybrid approach: GitHub Actions for PRs, Cloud Build for deployments

## Cost Monitoring Tools

### Estimate Your Actual Costs

Use the provided script to analyze your actual build history and estimate costs:

```bash
# Estimate costs for the last 30 days (default)
PROJECT_ID=your-project ./scripts/terraform/estimate_build_costs.sh

# Estimate costs for the last 7 days
PROJECT_ID=your-project DAYS=7 ./scripts/terraform/estimate_build_costs.sh
```

The script will:
- Count your builds and total build time
- Calculate actual costs based on your machine type
- Project monthly costs based on current usage
- Compare against documentation estimates
- Provide recommendations

### Budget Alert Setup Guide

To set up billing alerts in Google Cloud Console:

```bash
# First, find your billing account ID
gcloud billing accounts list

# Then create the budget (replace YOUR_BILLING_ACCOUNT_ID with actual ID from above)
gcloud billing budgets create \
  --billing-account=YOUR_BILLING_ACCOUNT_ID \
  --display-name="Cloud Build Monthly Budget" \
  --budget-amount=20 \
  --threshold-rule=percent=50 \
  --threshold-rule=percent=80 \
  --threshold-rule=percent=100
```

Or in the Console:
1. Go to **Billing** → **Budgets & alerts**
2. Create budget with name "Cloud Build Monthly Budget"
3. Set budget amount: $20
4. Add threshold rules: 50%, 80%, 100%
5. Configure email alerts

## Summary - The Bottom Line

**For typical usage (30 deployments/month)**:

- 💰 **Cost**: ~$5/month
- ⚡ **Speed**: 5-12 minute builds
- 🎯 **Value**: $0.67-1.00 per hour of developer time saved
- ✅ **Recommendation**: Keep current setup, it's already optimized
- 📊 **Action**: Set up budget alerts and monitor for 1-2 months

**This is a well-optimized, cost-effective build setup. No immediate changes needed.**
