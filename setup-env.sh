#!/bin/bash

# Environment bootstrap for the Cloud Function deployment and local development.
# The script is sourced by other tools, so avoid executing commands with side effects.

# Default Cloud Function name if not provided by the caller.
: "${CLOUD_FUNCTION_NAME:=receiptProcessingFunction}"
export CLOUD_FUNCTION_NAME

