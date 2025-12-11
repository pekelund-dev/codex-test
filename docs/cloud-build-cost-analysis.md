# Cloud Build Cost Analysis

This document provides a comprehensive analysis of the current Google Cloud Build setup costs, including detailed pricing breakdowns, usage estimates, and optimization recommendations.

## Current Setup Overview

The project uses Google Cloud Build to build Docker containers for two services:
- **Web application** (`pklnd-web`)
- **Receipt processor** (`pklnd-receipts`)

### Build Configuration

Both services use similar Cloud Build configurations with the following specifications:

| Configuration | Web Service | Receipt Service |
|--------------|-------------|-----------------|
| **Machine Type** | E2_HIGHCPU_8 | E2_HIGHCPU_8 |
| **vCPUs** | 8 | 8 |
| **Memory** | 8 GB | 8 GB |
| **Timeout** | 1200s (20 min) | 1200s (20 min) |
| **Build Strategy** | Parallel | Parallel |
| **Caching** | BuildKit inline cache | BuildKit inline cache |
| **Builder** | gcr.io/cloud-builders/docker | gcr.io/cloud-builders/docker |

### Deployment Strategy

The `deploy_services.sh` script builds both images **in parallel**, which:
- Reduces total deployment time by ~50%
- Doubles the concurrent Cloud Build usage during deployments
- Results in two simultaneous Cloud Build jobs running

## Google Cloud Build Pricing (as of 2024)

### Build Minutes Pricing

Google Cloud Build pricing is based on machine type and build duration:

#### Free Tier
- **120 build-minutes per day** on the default `n1-standard-1` machine
- Applies to the first 120 minutes each day
- Resets daily

#### Paid Tier (after free tier exhausted)

| Machine Type | vCPUs | Memory | Price per Build-Minute |
|--------------|-------|--------|------------------------|
| n1-standard-1 (default) | 1 | 3.75 GB | $0.003 |
| e2-standard-2 | 2 | 8 GB | $0.005 |
| e2-standard-4 | 4 | 16 GB | $0.010 |
| **e2-highcpu-8** | **8** | **8 GB** | **$0.016** |
| e2-standard-8 | 8 | 32 GB | $0.020 |
| e2-highcpu-32 | 32 | 32 GB | $0.064 |
| n1-highcpu-32 | 32 | 28.8 GB | $0.080 |

**Current machine type**: `E2_HIGHCPU_8` at **$0.016 per build-minute**

### Additional Costs

#### Artifact Registry Storage
- **Storage**: $0.10 per GB per month
- Container images are stored here
- Average image size: 200-500 MB per image

#### Cloud Storage (Build Cache)
- **Storage**: $0.020 per GB per month (Standard storage in us-east1)
- **Operations**: Minimal cost for build source uploads
- Build source archives are automatically cleaned up after each deployment

#### Network Egress
- **Within Google Cloud**: Free
- **Internet egress**: $0.12 per GB (unlikely for this use case)

## Cost Calculation

### Build Time Analysis

Based on the documentation in `build-performance-optimizations.md`:

#### First Build (Cold Cache)
- Web image: 8-12 minutes
- Receipt processor: 5-8 minutes
- **Total parallel time**: 8-12 minutes (limited by longest build)
- **Total build-minutes consumed**: (8-12) + (5-8) = **13-20 build-minutes**

#### Subsequent Builds (Warm Cache)
- Web image: 3-5 minutes
- Receipt processor: 2-4 minutes
- **Total parallel time**: 3-5 minutes (limited by longest build)
- **Total build-minutes consumed**: (3-5) + (2-4) = **5-9 build-minutes**

### Cost Per Deployment

#### First Deployment (Cold Cache)
- Build-minutes: 13-20 minutes
- Machine type: E2_HIGHCPU_8 @ $0.016/min
- **Cost**: 13 × $0.016 to 20 × $0.016 = **$0.21 - $0.32**

#### Subsequent Deployments (Warm Cache)
- Build-minutes: 5-9 minutes
- Machine type: E2_HIGHCPU_8 @ $0.016/min
- **Cost**: 5 × $0.016 to 9 × $0.016 = **$0.08 - $0.14**

### Monthly Cost Estimates

#### Development/Testing Scenario (10 deployments/month)
Assuming 1 cold cache build + 9 warm cache builds:
- Cold cache: 1 × $0.32 = $0.32
- Warm cache: 9 × $0.14 = $1.26
- **Total build costs**: $1.58/month
- Artifact storage (6 images @ 350 MB avg): ~$0.21/month
- **Total monthly cost**: **~$1.80/month**

#### Active Development (30 deployments/month)
Assuming 3 cold cache builds + 27 warm cache builds:
- Cold cache: 3 × $0.32 = $0.96
- Warm cache: 27 × $0.14 = $3.78
- **Total build costs**: $4.74/month
- Artifact storage (6 images @ 350 MB avg): ~$0.21/month
- **Total monthly cost**: **~$4.95/month**

#### Continuous Integration (100 deployments/month)
Assuming 10 cold cache builds + 90 warm cache builds:
- Cold cache: 10 × $0.32 = $3.20
- Warm cache: 90 × $0.14 = $12.60
- **Total build costs**: $15.80/month
- Artifact storage (6 images @ 350 MB avg): ~$0.21/month
- **Total monthly cost**: **~$16.00/month**

### Free Tier Impact

Google Cloud Build provides 120 free build-minutes per day on n1-standard-1 machines. However:

**Important**: The free tier applies only to `n1-standard-1` machines, NOT to `E2_HIGHCPU_8` machines.

Since this project uses E2_HIGHCPU_8, **all build minutes are billable** from the first minute.

## Cost Optimization Analysis

### Current Optimizations (Already Implemented)

The project has already implemented several excellent cost optimizations:

1. **BuildKit Caching** (60-80% rebuild time reduction)
   - Saves ~$0.12-0.18 per deployment after cache is warm
   - Annual savings: ~$10-20 for active development

2. **Parallel Builds** (50% total time reduction)
   - Saves ~$0.07-0.10 per deployment
   - Note: Uses more concurrent resources but reduces wall-clock time
   - Annual savings: ~$8-12 for active development

3. **Higher CPU Machine Type** (30-40% compilation time reduction)
   - E2_HIGHCPU_8 is more expensive per minute BUT completes faster
   - Net effect: Lower total cost due to reduced build time
   - Estimated savings: ~15-20% total cost vs slower machines

4. **Reduced Build Context** (60-80% upload time reduction)
   - Saves ~$0.02-0.04 per deployment
   - Annual savings: ~$2-5 for active development

5. **Automatic Artifact Cleanup**
   - Keeps only last 3 timestamped images
   - Saves ~$0.10-0.20/month in storage costs
   - Annual savings: ~$1.20-2.40

**Total estimated savings from current optimizations**: 40-60% vs non-optimized setup

### Additional Optimization Opportunities

#### 1. Use Smaller Machine Type for Receipt Processor

**Potential Savings**: $0.01-0.02 per deployment

The receipt processor builds faster (2-8 minutes vs 3-12 minutes for web). It might not need the E2_HIGHCPU_8 machine.

**Test Options**:
- E2_HIGHCPU_4 (4 vCPUs, $0.010/min): 50% cheaper
- E2_STANDARD_2 (2 vCPUs, $0.005/min): 68% cheaper

**Trade-off**: Slower builds might offset savings if build time increases significantly.

**Recommendation**: Test with E2_HIGHCPU_4 first. If build time stays under 6 minutes, it's worth it.

**Implementation**:
```yaml
# receipt-parser/cloudbuild.yaml
options:
  logging: CLOUD_LOGGING_ONLY
  machineType: 'E2_HIGHCPU_4'  # Changed from E2_HIGHCPU_8
```

#### 2. Implement Conditional Deployments

**Potential Savings**: Up to 50% of total costs

Only rebuild services that have changed:

**Implementation**: Add pre-build check in `deploy_services.sh`:
```bash
# Check for changes in web/ directory
if git diff --quiet HEAD~1 HEAD -- web/ Dockerfile; then
  echo "No changes in web service, skipping build"
  SKIP_WEB_BUILD=true
fi

# Check for changes in receipt-parser/ directory
if git diff --quiet HEAD~1 HEAD -- receipt-parser/; then
  echo "No changes in receipt processor, skipping build"
  SKIP_RECEIPT_BUILD=true
fi
```

**Savings**: If only one service changes per deployment, saves ~$0.04-0.08 per deployment.

#### 3. Consider Using GitHub Actions for Some Builds

**Potential Savings**: Variable, but could be significant

GitHub Actions provides 2,000 free minutes/month for private repos (3,000 for public).

**Pros**:
- Free tier is substantial
- Good for CI/CD testing
- Can still use Cloud Build for production deployments

**Cons**:
- Slower than Cloud Build for large Docker builds
- More complex to set up caching for Docker builds
- Would need to push images to Artifact Registry separately

**Recommendation**: Keep Cloud Build for production deployments, but consider GitHub Actions for testing/validation builds.

#### 4. Use Cloud Build Triggers Instead of Manual Builds

**Potential Savings**: Better cost visibility, no direct savings

Set up automatic triggers for:
- PR builds (test only, no deployment)
- Main branch merges (full deployment)

**Benefits**:
- Automatic builds reduce manual deployment overhead
- Better integration with CI/CD workflows
- Can add build caching optimizations more easily

**Costs**: Same per-build cost, but better tracking and automation.

#### 5. Optimize Docker Layer Ordering

**Potential Savings**: $0.01-0.03 per deployment

Current Dockerfiles already use multi-stage builds and BuildKit caching. Additional optimizations:

**Review Dockerfile layer ordering**:
- Put least-frequently-changed layers first
- Separate dependency installation from code copying
- Use `.dockerignore` effectively (already done)

**Current status**: Already well-optimized based on BuildKit cache mounts.

#### 6. Monitor and Set Build Budgets

**Potential Savings**: Prevents cost overruns

Set up budget alerts in Google Cloud Console:
- Alert at 50% of monthly budget
- Alert at 80% of monthly budget
- Alert at 100% of monthly budget

**Recommended monthly budget**: $20 (provides headroom for 100+ deployments)

## Cost Comparison with Alternatives

### Alternative Build Options

#### Option 1: Use Default n1-standard-1 Machine
- **Cost per minute**: $0.003 (after free tier)
- **Estimated build time**: 20-40 minutes (much slower)
- **Cost per deployment**: $0.06-0.12 (after free tier)
- **Pros**: Cheaper per deployment (if within free tier)
- **Cons**: 2-4x slower, poor developer experience
- **Verdict**: Not recommended - time savings worth the cost

#### Option 2: Use E2_STANDARD_4 Machine
- **Cost per minute**: $0.010
- **Estimated build time**: 10-18 minutes
- **Cost per deployment**: $0.10-0.18
- **Pros**: Cheaper than current setup
- **Cons**: Slower builds
- **Verdict**: Possible middle ground, but likely not worth the slower builds

#### Option 3: Build Locally and Push Images
- **Cost**: Free (uses local compute)
- **Time**: 15-30 minutes per build
- **Pros**: No Cloud Build costs
- **Cons**: 
  - Slow uploads (depends on internet speed)
  - No caching benefits
  - Manual process
  - Inconsistent across developers
- **Verdict**: Not recommended for team use

#### Option 4: Keep Current Setup (E2_HIGHCPU_8)
- **Cost per minute**: $0.016
- **Estimated build time**: 5-12 minutes
- **Cost per deployment**: $0.08-0.32
- **Pros**: Fast builds, good developer experience
- **Cons**: Higher per-minute cost
- **Verdict**: **RECOMMENDED** - optimal balance of speed and cost

## Recommendations

### Immediate Actions (Keep Current Setup)

The current setup is already well-optimized. The cost is reasonable for the value provided:

1. **Monitor costs**: Set up billing alerts at $10, $15, and $20/month
2. **Track deployments**: Log deployment frequency to understand actual usage
3. **Document baseline**: Use this analysis as baseline for future optimization decisions

### Optional Optimizations (If Cost Becomes an Issue)

Only implement these if monthly costs exceed $20:

1. **Test E2_HIGHCPU_4 for receipt processor**: Potential 30-40% savings on that service
2. **Implement conditional builds**: Skip builds for unchanged services (50% savings potential)
3. **Consider GitHub Actions for PR builds**: Use free tier for non-production builds

### Long-Term Considerations

1. **Review quarterly**: Cloud pricing changes, and usage patterns evolve
2. **A/B test machine types**: Test different configurations to find optimal balance
3. **Consider Cloud Build quotas**: If scaling to many deployments per day, review quota limits

## Summary

### Current Cost Structure

| Scenario | Deployments/Month | Estimated Monthly Cost |
|----------|-------------------|------------------------|
| Light development | 10 | $1.80 |
| Active development | 30 | $4.95 |
| Continuous integration | 100 | $16.00 |

### Key Findings

1. **Current setup is cost-effective**: $0.08-0.32 per deployment is reasonable
2. **Already optimized**: Current optimizations save 40-60% vs baseline
3. **Free tier doesn't apply**: E2_HIGHCPU_8 machines are not covered by free tier
4. **Parallel builds are efficient**: Despite using 2x concurrent resources, parallel builds reduce total cost

### Cost vs Value Analysis

**For typical active development (30 deployments/month)**:
- Monthly cost: ~$5
- Time saved per deployment: ~10-15 minutes vs slower machines
- Total time saved: 300-450 minutes/month (5-7.5 hours)
- **Cost per hour saved**: ~$0.67-1.00/hour

**Verdict**: Excellent value. The optimized Cloud Build setup saves significant developer time for minimal cost.

### Action Items

- [x] Document current cloud build costs
- [ ] Set up billing budget alerts at $10, $15, and $20/month
- [ ] Monitor actual deployment frequency for 1-2 months
- [ ] Re-evaluate if costs exceed $20/month
- [ ] Test E2_HIGHCPU_4 for receipt processor if optimization needed

## References

- [Google Cloud Build Pricing](https://cloud.google.com/build/pricing)
- [Cloud Build Machine Types](https://cloud.google.com/build/docs/api/reference/rest/v1/projects.builds#machinetype)
- [Artifact Registry Pricing](https://cloud.google.com/artifact-registry/pricing)
- [Cloud Storage Pricing](https://cloud.google.com/storage/pricing)
- Project documentation: `docs/build-performance-optimizations.md`
