# Request Tracing Dashboards

This guide outlines a starter Cloud Monitoring dashboard for the web and receipt-parser services. Use it as a baseline for latency, error rate, and throughput visibility, and tune thresholds as production traffic patterns emerge.

## Dashboard layout (single page)

Create a Cloud Monitoring dashboard with the following panels. Keep the filters consistent across panels by scoping to the Cloud Run service (`web` or `receipt-parser`) and region.

### 1) Request latency

**Panels**
- HTTP request latency (p50, p95, p99)
- Handler latency (optional split by route)

**Suggested metrics**
- `run.googleapis.com/request_latencies` (distribution)
- Filter: `resource.type="cloud_run_revision"`

**Alert thresholds (starting points)**
- p95 latency > 750 ms for 5 minutes (warning)
- p99 latency > 1500 ms for 5 minutes (critical)

### 2) Error rate

**Panels**
- 4xx/5xx error counts over time
- Error rate (%)

**Suggested metrics**
- `run.googleapis.com/request_count` with response code labels
- Filter: `response_code_class="5xx"`

**Alert thresholds (starting points)**
- 5xx rate > 2% of total requests for 5 minutes (warning)
- 5xx rate > 5% of total requests for 5 minutes (critical)

### 3) Throughput

**Panels**
- Requests per second (RPS)
- Receipt processing throughput (if available)

**Suggested metrics**
- `run.googleapis.com/request_count` (align to 1-minute rate)
- For receipt-parser, add a log-based metric for processed receipt events if needed

**Alert thresholds (starting points)**
- Sudden drop in RPS > 50% compared to previous 15-minute average (warning)
- Sustained zero RPS for 10 minutes (critical, only if traffic is expected)

### 4) Resource saturation

**Panels**
- CPU utilization
- Memory utilization
- Instance count

**Suggested metrics**
- `run.googleapis.com/container/cpu/utilizations`
- `run.googleapis.com/container/memory/utilizations`
- `run.googleapis.com/container/instance_count`

**Alert thresholds (starting points)**
- CPU > 80% for 10 minutes (warning)
- Memory > 80% for 10 minutes (warning)
- Instance count >= max instance setting for 10 minutes (warning)

### 5) Dependency health (optional)

**Panels**
- Firestore request latency (client-side timing or Google-managed metrics)
- Cloud Storage errors (for receipt-parser ingestion)

**Suggested metrics**
- Cloud Trace or log-based metrics for Firestore latency if instrumentation is enabled
- `storage.googleapis.com/api/request_count` with 4xx/5xx response codes

**Alert thresholds (starting points)**
- Firestore latency p95 > 1000 ms for 5 minutes (warning)
- Storage 5xx rate > 1% for 5 minutes (warning)

## Notes on tuning

- Start with the thresholds above, then adjust based on real traffic volumes.
- Ensure alert policies are scoped to production environments only.
- Pair alerting with Cloud Trace sampling to speed up incident triage.
