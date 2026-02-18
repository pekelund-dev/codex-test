# Do NOT include app_secret_json here; supply it via the APP_SECRET_FILE env var
# in apply_infrastructure.sh to avoid committing credentials.

# ── Required ─────────────────────────────────────────────────────────────────
project_id = "codex-test-473008"

# ── Budget alerts (optional) ──────────────────────────────────────────────────
# Uncomment and fill in to enable GCP budget alerts with automatic shutdown.
# enable_budget_alert          = true
# budget_amount                = 1       # Monthly limit in USD
# billing_account_display_name = "My Billing Account"  # gcloud billing accounts list

# ── Overrides (all have sensible defaults) ────────────────────────────────────
# region                  = "us-east1"
# firestore_location      = "us-east1"
# firestore_database_name = "receipts-db"
# bucket_name             = ""   # defaults to pklnd-receipts-<project_id>
