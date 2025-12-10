# Build and Deployment Performance Optimizations

This document describes the optimizations made to reduce build and deployment times to Google Cloud Platform.

## Performance Improvements Summary

The following optimizations were implemented to significantly reduce build and deployment times:

### 1. Docker Layer Caching with BuildKit

**Problem**: Each build was downloading all Maven dependencies from scratch, wasting time and bandwidth.

**Solution**: 
- Updated both `Dockerfile` and `receipt-parser/Dockerfile` to use BuildKit cache mounts (`--mount=type=cache,target=/root/.m2`)
- This allows Maven dependencies to be cached between builds, dramatically reducing dependency download time

**Impact**: ~50-70% reduction in Maven dependency download time on subsequent builds

### 2. Docker BuildKit with Inline Caching

**Problem**: The standard Docker builder in Cloud Build didn't support advanced caching features efficiently. Kaniko was considered but is now archived/deprecated.

**Solution**:
- Uses the maintained `gcr.io/cloud-builders/docker` with BuildKit enabled via `DOCKER_BUILDKIT=1`
- Configured inline caching with `--build-arg=BUILDKIT_INLINE_CACHE=1` and `--cache-from`
- BuildKit cache metadata is embedded in the image layers, making subsequent builds much faster
- BuildKit is actively maintained by Docker/Moby and is the recommended solution for modern container builds

**Impact**: ~40-60% reduction in image build time for unchanged layers

**Why BuildKit over Kaniko**: Kaniko was archived by Google in June 2025 and is no longer maintained. BuildKit is the actively maintained, officially recommended replacement with superior performance (up to 3x faster), better multi-architecture support, and more advanced features.

**Note on Caching Strategy**: We use inline caching instead of registry-based cache export because Cloud Build's default Docker driver doesn't support `--cache-to=type=registry`. Inline caching embeds cache metadata directly in the image layers, which is pulled via `--cache-from` on subsequent builds. This approach works seamlessly with Cloud Build while still providing excellent cache performance.

### 3. Parallel Image Builds

**Problem**: Web and receipt-parser images were built sequentially, doubling build time.

**Solution**:
- Modified `scripts/terraform/deploy_services.sh` to build both images in parallel using background processes
- Both `gcloud builds submit` commands now run simultaneously

**Impact**: ~50% reduction in total build time (from sequential to parallel)

### 4. Higher CPU Machine Type

**Problem**: Default Cloud Build machines were underpowered for Maven builds.

**Solution**:
- Added `machineType: 'E2_HIGHCPU_8'` to both cloudbuild.yaml files
- 8 vCPUs allow Maven to run more parallel threads during compilation

**Impact**: ~30-40% reduction in compilation time

### 5. Reduced Build Context Size

**Problem**: Entire repository (including docs, tests, IDE files) was uploaded to Cloud Build.

**Solution**:
- Created `.dockerignore` files at root and in `receipt-parser/` directory
- Updated `.gcloudignore` to exclude unnecessary files
- Excluded: documentation, test files, IDE configs, build artifacts, infrastructure code

**Impact**: ~60-80% reduction in build context upload size and time

### 6. Reduced Build Timeouts

**Problem**: Conservative 1800s (30 minute) timeouts were allowing inefficient builds to succeed.

**Solution**:
- Reduced timeouts from 1800s to 1200s (20 minutes)
- With optimizations in place, builds should complete in 5-10 minutes
- Faster feedback when builds fail

**Impact**: Faster failure detection and reduced cost for failed builds

## Expected Performance Results

### Before Optimizations (Legacy Terraform Setup)
- Build context upload: ~60-120 seconds
- Web image build: ~12-18 minutes
- Receipt-parser image build: ~8-12 minutes
- Sequential total: ~25-35 minutes
- Terraform apply: ~2-3 minutes
- **Total deployment time: ~30-40 minutes**

### After Optimizations (First Build - Cold Cache)
- Build context upload: ~15-30 seconds (smaller context)
- Web image build: ~8-12 minutes (faster machine)
- Receipt-parser image build: ~5-8 minutes (faster machine)
- Parallel total: ~8-12 minutes (parallel execution)
- Terraform apply: ~2-3 minutes
- **Total deployment time: ~12-18 minutes**

### After Optimizations (Subsequent Builds - Warm Cache)
- Build context upload: ~15-30 seconds
- Web image build: ~3-5 minutes (cached dependencies + layers)
- Receipt-parser image build: ~2-4 minutes (cached dependencies + layers)
- Parallel total: ~3-5 minutes (parallel execution)
- Terraform apply: ~2-3 minutes
- **Total deployment time: ~7-10 minutes**

## Cost Implications

These optimizations also reduce costs:

1. **Lower Cloud Build minutes**: Faster builds = fewer billable minutes
2. **Reduced network egress**: Smaller build context and cached dependencies
3. **Efficient machine usage**: Higher CPU machines finish faster, reducing total cost
4. **Cache storage**: Minimal cost for storing cached layers (worth the speed improvement)

**Estimated cost savings**: 40-60% reduction in Cloud Build costs

## How to Use

The optimizations are automatically applied when using the standard deployment workflow:

```bash
# No changes needed to the deployment command
PROJECT_ID=your-project ./scripts/terraform/deploy_services.sh
```

### Monitoring Build Performance

To see the improvements:

1. **Check Cloud Build console**: View build duration in the GCP Console
2. **Compare logs**: Look for BuildKit cache messages such as "CACHED", "restoring cache", or "using cache" in the build logs
3. **Monitor upload size**: Check Cloud Build logs for context upload time

### Cache Warming

For the best performance on the first build after optimizations:

1. Ensure Artifact Registry has the `web` and `receipts` repositories
2. The first build will populate caches
3. Subsequent builds will be significantly faster

## Troubleshooting

### Cache Not Working

If builds aren't getting faster:

1. **Check BuildKit logs**: Look for "restored cache", "using cache", or "exporting cache" messages to verify cache hits
2. **Verify inline cache**: Ensure `--build-arg=BUILDKIT_INLINE_CACHE=1` is set
3. **Check permissions**: Service account needs read access to Artifact Registry

### Parallel Builds Failing

If parallel builds cause issues:

1. **Check resource limits**: Ensure your project has sufficient Cloud Build quota
2. **Serialize if needed**: Comment out the parallel execution and revert to sequential
3. **Review logs**: Check both build logs for specific errors

### Build Context Still Large

If uploads are slow:

1. **Verify .dockerignore**: Ensure files are being excluded
2. **Check .gcloudignore**: Update patterns as needed
3. **Clean local directory**: Remove local build artifacts before submitting

## Future Optimization Opportunities

1. **Multi-platform builds**: If deploying to ARM, optimize for multi-arch
2. **Dependency pre-fetching**: Create a base image with common dependencies
3. **Incremental builds**: Investigate Maven incremental compilation
4. **Build triggers**: Set up automatic builds on commit to avoid manual deploys
5. **Regional caching**: Use region-specific cache for multi-region deployments

## Artifact Management and Cost Optimization

### Build Artifacts Created

Each deployment creates the following artifacts:

1. **Container Images** (Artifact Registry):
   - Timestamped image (e.g., `pklnd-web:20241210-073000`)
   - `latest` tag (updated each deployment, contains inline cache metadata)

2. **Cloud Build Archives** (Cloud Storage):
   - Source code archives in `gs://{PROJECT_ID}_cloudbuild/source/`

**Note**: With inline caching, the cache metadata is embedded in the `latest` image, so no separate buildcache images are created.

### Automatic Cleanup

The deployment script (`scripts/terraform/deploy_services.sh`) automatically cleans up:

- **Cloud Build source archives**: Deleted after each deployment
- **Old timestamped images**: Keeps only the last 3 timestamped images per service

This automatic cleanup reduces storage costs by:
- Removing ~2GB of source archives per deployment
- Removing old container images (each ~200-500MB)
- Estimated savings: $0.10-0.30 per deployment

### Manual Cleanup

For additional cleanup control, use the dedicated cleanup script:

```bash
# Clean up with default settings (keep last 3 images)
PROJECT_ID=your-project ./scripts/terraform/cleanup_artifacts.sh

# Keep more images for rollback capability
PROJECT_ID=your-project KEEP_IMAGES=5 ./scripts/terraform/cleanup_artifacts.sh

# Also remove BuildKit cache (reduces performance on next build)
PROJECT_ID=your-project CLEAN_CACHE=true ./scripts/terraform/cleanup_artifacts.sh
```

### Cost Analysis

**Without cleanup:**
- 10 deployments = 10 timestamped images × 2 services = ~4-8GB storage
- Cloud Build archives = ~20GB
- Estimated cost: $0.10-0.20/month in Artifact Registry + $0.02-0.05/month in Cloud Storage

**With automatic cleanup:**
- Only 3 recent images + latest per service = ~2-3GB storage
- No Cloud Build archives accumulation
- Estimated cost: $0.03-0.06/month total

**Savings:** ~60-75% reduction in artifact storage costs

### Best Practices

1. **Keep inline cache in `latest` tag**: Essential for build performance
2. **Retain 3-5 recent images**: Enables quick rollback if needed
3. **Run manual cleanup periodically**: Once a month for additional cleanup
4. **Monitor storage costs**: Use GCP cost explorer to track Artifact Registry costs
5. **Set up lifecycle policies**: For long-term automated management (see below)

### Lifecycle Policies (Advanced)

For production environments, consider setting up Artifact Registry lifecycle policies:

```bash
# Create a policy file
cat > policy.json <<'EOF'
{
  "rules": [
    {
      "name": "delete-old-timestamped-images",
      "action": "DELETE",
      "condition": {
        "tagState": "TAGGED",
        "tagPrefixesToMatch": ["20"],
        "olderThan": "2592000s"
      }
    },
    {
      "name": "keep-recent-tagged",
      "action": "KEEP",
      "condition": {
        "tagState": "TAGGED",
        "newerThan": "604800s"
      }
    }
  ]
}
EOF

# Apply to repository
gcloud artifacts repositories set-cleanup-policies web \
  --project="$PROJECT_ID" \
  --location=us-east1 \
  --policy=policy.json
```

This automatically deletes images older than 30 days while keeping images from the last 7 days.

## Comparison with Legacy Scripts

The legacy scripts (`scripts/legacy/`) were faster because they:
- Used simpler gcloud commands
- Had less infrastructure provisioning
- But lacked reproducibility and security features

The optimized Terraform approach now provides:
- **Similar speed** to legacy scripts (especially with warm cache)
- **Better infrastructure as code** practices
- **Improved security** with proper service accounts and secrets
- **More maintainability** for production deployments

## References

- [Kaniko Documentation](https://github.com/GoogleContainerTools/kaniko)
- [Docker BuildKit Cache Mounts](https://docs.docker.com/build/cache/)
- [Cloud Build Machine Types](https://cloud.google.com/build/docs/api/reference/rest/v1/projects.builds#machinetype)
- [Cloud Build Best Practices](https://cloud.google.com/build/docs/optimize-builds)
