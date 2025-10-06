# Useful Bash Commands

```bash
# Bootstrap the entire GCP environment from scratch
clear && source ./setup-env.sh && \
    ./scripts/deploy_cloud_run.sh && \
    ./scripts/deploy_cloud_function.sh

# Tear everything down (set DELETE_* flags if you also want service accounts or
# Artifact Registry repositories removed)
clear && source ./setup-env.sh && \
    ./scripts/teardown_gcp_resources.sh

clear && git pull && ./scripts/deploy_cloud_function.sh && \
    gsutil cp ./test-receipt.pdf "gs://$GCS_BUCKET/receipts/large-test-receipt.pdf"

clear && git pull && ./mvnw -pl function -am spring-boot:run \
    -Dspring-boot.run.profiles=local-receipt-test

clear && curl -F "file=@test-receipt.pdf" http://localhost:8080/local-receipts/parse | jq

# Local-only workflow (run the emulator in its own terminal)
./scripts/start_firestore_emulator.sh

clear && source ./scripts/source_local_env.sh && \
    ./mvnw -Pinclude-web -pl web -am spring-boot:run
```

These shortcuts assume that the appropriate environment helper has been sourced
(`setup-env.sh` for GCP deployments or `scripts/source_local_env.sh` for the
emulator workflow) so that `PROJECT_ID`, `GCS_BUCKET`, and other required
environment variables are available before the commands are executed.
