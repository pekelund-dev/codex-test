FROM eclipse-temurin:21-jre

# Install Node.js, firebase-tools, and tini
RUN set -eux; \
  apt-get update; \
  apt-get install -y --no-install-recommends \
    curl \
    gnupg \
    netcat-openbsd \
    tini; \
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -; \
  apt-get install -y nodejs; \
  npm install -g firebase-tools; \
  rm -rf /var/lib/apt/lists/*

# Create data directory with proper permissions
RUN mkdir -p /data && chmod 777 /data

COPY local/firestore-emulator/entrypoint.sh /entrypoint.sh
COPY local/firestore-emulator/firebase.json /firebase.json
RUN chmod +x /entrypoint.sh

WORKDIR /

ENTRYPOINT ["/usr/bin/tini", "--", "/entrypoint.sh"]
