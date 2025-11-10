# Useful Bash Commands

```bash
# Bootstrap the entire GCP environment from scratch
clear && source ./setup-env.sh && \
    ./scripts/deploy_cloud_run.sh && \
    ./scripts/deploy_receipt_processor.sh && \
    ./scripts/cleanup_artifact_repos.sh

# Tear everything down (set DELETE_* flags if you also want service accounts or
# Artifact Registry repositories removed)
clear && source ./setup-env.sh && \
    ./scripts/teardown_gcp_resources.sh

clear && git pull && ./scripts/deploy_receipt_processor.sh && \
    gsutil cp ./test-receipt.pdf "gs://$GCS_BUCKET/receipts/large-test-receipt.pdf"

clear && git pull && ./mvnw -pl receipt-parser -am spring-boot:run \
    -Dspring-boot.run.profiles=local-receipt-test

clear && curl -F "file=@test-receipt.pdf" http://localhost:8080/local-receipts/parse | jq

# Local-only workflow (run the emulator in its own terminal)
./scripts/start_firestore_emulator.sh

clear && source ./scripts/source_local_env.sh && \
    ./mvnw -Pinclude-web -pl web -am spring-boot:run

# Load locally stored service-account and OAuth credentials before running other helpers
clear && export FIRESTORE_CREDENTIALS_FILE=$HOME/.config/pklnd/firestore.json && \
    export GOOGLE_OAUTH_CREDENTIALS_FILE=$HOME/.config/pklnd/oauth-client.json && \
    source ./scripts/load_local_secrets.sh
```

These shortcuts assume that the appropriate environment helper has been sourced
(`setup-env.sh` for GCP deployments, `scripts/source_local_env.sh` for the
emulator workflow) and, when required, that `scripts/load_local_secrets.sh` has
populated credentials from any locally stored JSON files so `PROJECT_ID`,
`GCS_BUCKET`, and other required environment variables are available before the
commands are executed.
