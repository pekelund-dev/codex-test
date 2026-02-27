# Add Cloud Monitoring alerts

- [x] infra/terraform/infrastructure/monitoring.tf created
  - monitoring.googleapis.com API enabled
  - alert_email variable (optional) for notifications
  - Alert: web_error_rate (5xx > 5% over 5 min)
  - Alert: web_latency (P95 > 5s over 5 min)
  - Alert: web_unavailable (0 instances for 2 min)
