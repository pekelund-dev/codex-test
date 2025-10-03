# Useful Bash Commands

```bash
clear && git pull && ./deploy-cloud-function.sh && gsutil cp ./test-receipt.pdf "gs://$GCS_BUCKET/receipts/large-test-receipt.pdf"

clear && git pull && ./mvnw -pl function -am spring-boot:run \
    -Dspring-boot.run.profiles=local-receipt-test

clear && curl -F "file=@test-receipt.pdf" http://localhost:8080/local-receipts/parse | jq
```

These shortcuts assume that `setup-env.sh` has been sourced so that `GCS_BUCKET`
and other required environment variables are available.
