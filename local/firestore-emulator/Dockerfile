FROM gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators

RUN set -eux; \
  apt-get update; \
  apt-get install -y --no-install-recommends \
    google-cloud-sdk-firestore-emulator \
    netcat-openbsd; \
  rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["gcloud", "beta", "emulators", "firestore", "start"]
CMD ["--host-port=0.0.0.0:8085", "--project=pklnd-local", "--quiet", "--data-dir=/opt/data"]
