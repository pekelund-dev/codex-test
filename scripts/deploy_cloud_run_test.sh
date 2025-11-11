#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export DEPLOY_ENVIRONMENT="${DEPLOY_ENVIRONMENT:-staging}"
exec "${SCRIPT_DIR}/deploy_cloud_run.sh" --env "${DEPLOY_ENVIRONMENT}" "$@"
