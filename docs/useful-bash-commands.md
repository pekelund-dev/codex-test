# Useful Bash Commands

```bash
# Bootstrap the entire GCP environment from scratch with Terraform
clear && APP_SECRET_FILE=/tmp/pklnd-secret.json PROJECT_ID=$PROJECT_ID \
    ./scripts/terraform/apply_infrastructure.sh && \
    PROJECT_ID=$PROJECT_ID ./scripts/terraform/deploy_services.sh

# Tear everything down via Terraform
clear && PROJECT_ID=$PROJECT_ID terraform -chdir=infra/terraform/deployment destroy && \
    PROJECT_ID=$PROJECT_ID terraform -chdir=infra/terraform/infrastructure destroy

# Legacy helpers remain available under scripts/legacy if you need the previous
# gcloud-driven workflow
# clear && source ./setup-env.sh && ./scripts/legacy/deploy_cloud_run.sh

clear && git pull && ./scripts/legacy/deploy_receipt_processor.sh && \
    gsutil cp ./test-receipt.pdf "gs://$GCS_BUCKET/receipts/large-test-receipt.pdf"

clear && git pull && ./mvnw -pl receipt-parser -am spring-boot:run \
    -Dspring-boot.run.profiles=local-receipt-test

clear && curl -F "file=@test-receipt.pdf" http://localhost:8081/local-receipts/parse | jq

# Local-only workflow (run the emulator in its own terminal)
./scripts/legacy/start_firestore_emulator.sh

clear && source ./scripts/legacy/source_local_env.sh && \
    ./mvnw -Pinclude-web -pl web -am spring-boot:run

# Load locally stored service-account and OAuth credentials before running other helpers
clear && export FIRESTORE_CREDENTIALS_FILE=$HOME/.config/pklnd/firestore.json && \
    export GOOGLE_OAUTH_CREDENTIALS_FILE=$HOME/.config/pklnd/oauth-client.json && \
    source ./scripts/legacy/load_local_secrets.sh
```

These shortcuts assume that the appropriate environment helper has been sourced
(`setup-env.sh` for GCP deployments, `scripts/legacy/source_local_env.sh` for the
emulator workflow) and, when required, that `scripts/legacy/load_local_secrets.sh` has
populated credentials from any locally stored JSON files so `PROJECT_ID`,
`GCS_BUCKET`, and other required environment variables are available before the
commands are executed.
