# Do NOT include app_secret_json here; supply it via the APP_SECRET_FILE env var
# in apply_infrastructure.sh to avoid committing credentials.

# ── Required ─────────────────────────────────────────────────────────────────
project_id = "codex-test-473008"

# ── Budget alerts ─────────────────────────────────────────────────────────────
enable_budget_alert = true
budget_amount       = 25
billing_account_id  = "01021B-547B18-55D892"

# ── Overrides (all have sensible defaults) ────────────────────────────────────
# region                  = "us-east1"
# firestore_location      = "us-east1"
# firestore_database_name = "receipts-db"
# bucket_name             = ""   # defaults to pklnd-receipts-<project_id>
