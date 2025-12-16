#!/bin/bash
set -e

PROJECT_ID="${FIRESTORE_PROJECT_ID:-pklnd-local}"
DATA_DIR="/data/firestore-export"

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
