#!/bin/bash
set -e

PROJECT_ID="${FIRESTORE_PROJECT_ID:-pklnd-local}"
DATA_DIR="/data/firestore-export"

# Start socat forwarders to expose localhost-bound emulators to 0.0.0.0
# This allows the Hub to report 127.0.0.1 (pleasing browsers) while Docker maps the ports
# We use a loop to wait for the ports to be open before starting socat to avoid race conditions
# where socat grabs the port before the emulator does.

start_proxy() {
  local port=$1
  local ip=$(hostname -i)
  echo "Starting proxy for port $port on $ip..."
  # Wait for the port to be listening on localhost
  while ! nc -z 127.0.0.1 $port; do   
    sleep 0.1
  done
  echo "Port $port is open, starting socat..."
  socat TCP-LISTEN:$port,fork,bind=$ip TCP:127.0.0.1:$port &
}

start_proxy 8085 &
# start_proxy 4000 & # UI listens on 0.0.0.0 directly
start_proxy 4400 &
start_proxy 4500 &
start_proxy 9150 &

# Create export directory
mkdir -p "${DATA_DIR}"

# Check if export data exists
if [ -f "${DATA_DIR}/firebase-export-metadata.json" ]; then
  echo "Found existing export data, importing from ${DATA_DIR}..."
  exec firebase emulators:start \
    --only firestore \
    --project="${PROJECT_ID}" \
    --import="${DATA_DIR}" \
    --export-on-exit="${DATA_DIR}"
else
  echo "No export data found, starting fresh. Will export to ${DATA_DIR} on exit."
  exec firebase emulators:start \
    --only firestore \
    --project="${PROJECT_ID}" \
    --export-on-exit="${DATA_DIR}"
fi
