#!/usr/bin/env bash
# shellcheck disable=SC2034

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  echo "❌ This helper must be sourced: 'source scripts/load_local_secrets.sh'" >&2
  exit 1
fi

load_firestore_credentials() {
  if [[ -z "${FIRESTORE_CREDENTIALS_FILE:-}" ]]; then
    return
  fi

  if [[ ! -f "$FIRESTORE_CREDENTIALS_FILE" ]]; then
    echo "⚠️  FIRESTORE_CREDENTIALS_FILE points to '$FIRESTORE_CREDENTIALS_FILE' but the file was not found." >&2
    return
  fi

  if [[ "${FIRESTORE_CREDENTIALS:-}" != "file:${FIRESTORE_CREDENTIALS_FILE}" ]]; then
    export FIRESTORE_CREDENTIALS="file:${FIRESTORE_CREDENTIALS_FILE}"
  fi

  echo "✅ Loaded Firestore credentials from ${FIRESTORE_CREDENTIALS_FILE}"
}

load_google_oauth_credentials() {
  if [[ -z "${GOOGLE_OAUTH_CREDENTIALS_FILE:-}" ]]; then
    return
  fi

  if [[ ! -f "$GOOGLE_OAUTH_CREDENTIALS_FILE" ]]; then
    echo "⚠️  GOOGLE_OAUTH_CREDENTIALS_FILE points to '$GOOGLE_OAUTH_CREDENTIALS_FILE' but the file was not found." >&2
    return
  fi

  if [[ -n "${GOOGLE_CLIENT_ID:-}" && -n "${GOOGLE_CLIENT_SECRET:-}" ]]; then
    echo "ℹ️  GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are already set; skipping JSON parsing."
    return
  fi

  mapfile -t oauth_values < <(python3 - "$GOOGLE_OAUTH_CREDENTIALS_FILE" <<'PY'
import json
import os
import sys

path = sys.argv[1]
with open(path, 'r', encoding='utf-8') as handle:
    data = json.load(handle)

candidate = None
for key in ("web", "installed", "oauth_client"):
    if isinstance(data, dict) and key in data:
        candidate = data[key]
        break

if candidate is None:
    candidate = data

client_id = candidate.get("client_id")
client_secret = candidate.get("client_secret")

if not client_id or not client_secret:
    raise SystemExit("Missing client_id/client_secret in OAuth credentials JSON")

print(client_id)
print(client_secret)
PY
  ) || {
    echo "❌ Failed to parse OAuth client credentials JSON at ${GOOGLE_OAUTH_CREDENTIALS_FILE}" >&2
    return
  }

  if [[ ${#oauth_values[@]} -ge 2 ]]; then
    if [[ -z "${GOOGLE_CLIENT_ID:-}" ]]; then
      export GOOGLE_CLIENT_ID="${oauth_values[0]}"
    fi
    if [[ -z "${GOOGLE_CLIENT_SECRET:-}" ]]; then
      export GOOGLE_CLIENT_SECRET="${oauth_values[1]}"
    fi
    echo "✅ Extracted Google OAuth client from ${GOOGLE_OAUTH_CREDENTIALS_FILE}"
  fi
}

load_firestore_credentials
load_google_oauth_credentials

unset -f load_firestore_credentials
unset -f load_google_oauth_credentials
