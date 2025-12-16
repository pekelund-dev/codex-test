FROM gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators

RUN set -eux; \
  apt-get update; \
  apt-get install -y --no-install-recommends \
    default-jre-headless \
    netcat-openbsd; \
  gcloud components install cloud-firestore-emulator --quiet; \
  rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["gcloud", "beta", "emulators", "firestore", "start"]
CMD ["--host-port=0.0.0.0:8085", "--project=pklnd-local", "--quiet", "--data-dir=/opt/data"]
