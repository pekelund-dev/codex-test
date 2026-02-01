# Cloud Monitoring dashboard plan

## Goals

Provide a baseline Cloud Monitoring dashboard that highlights request latency, error rates, throughput, and dependency health for the web and receipt-parser services. Use the dashboard as a shared view for on-call triage and release validation.

## Recommended charts

### Service health overview

- **HTTP request latency (p50/p95/p99)**
  - Metric: `run.googleapis.com/request_latencies`
  - Group by: service name, location
  - Suggested thresholds:
    - Warning: p95 > 1.5s for 5 minutes
    - Critical: p95 > 3s for 5 minutes
- **HTTP error rate (5xx)**
  - Metric: `run.googleapis.com/request_count`
  - Filter: `response_code_class = "5xx"`
  - Group by: service name
  - Suggested thresholds:
    - Warning: 5xx > 1% of requests for 5 minutes
    - Critical: 5xx > 5% of requests for 5 minutes
- **Request throughput**
  - Metric: `run.googleapis.com/request_count`
  - Group by: service name
  - Use as context when investigating latency or error spikes.

### Dependencies

- **Firestore latency**
  - Metric: `firestore.googleapis.com/api/request_latencies`
  - Filter: `database_id = "(default)"`
  - Suggested thresholds:
    - Warning: p95 > 750ms for 10 minutes
    - Critical: p95 > 1.5s for 10 minutes
- **Cloud Storage operations errors**
  - Metric: `storage.googleapis.com/api/request_count`
  - Filter: `response_code_class = "5xx"`
  - Suggested thresholds:
    - Warning: 5xx > 1% for 10 minutes
    - Critical: 5xx > 5% for 10 minutes

### Background processing

- **Receipt parser error rate**
  - Metric: `run.googleapis.com/request_count`
  - Filter: `service_name = "receipt-parser"` and `response_code_class = "5xx"`
  - Suggested thresholds:
    - Warning: 5xx > 1% for 10 minutes
    - Critical: 5xx > 5% for 10 minutes
- **Receipt parser latency**
  - Metric: `run.googleapis.com/request_latencies`
  - Filter: `service_name = "receipt-parser"`
  - Suggested thresholds:
    - Warning: p95 > 2s for 10 minutes
    - Critical: p95 > 5s for 10 minutes

## Alert routing

- Route warnings to Slack with business-hours paging.
- Route critical alerts to on-call paging immediately.
- Add links to relevant runbooks (incident response, rollback steps, dependency status pages).

## Maintenance

- Review thresholds quarterly and after major traffic changes.
- Add charts for new dependencies or background jobs when they are introduced.
