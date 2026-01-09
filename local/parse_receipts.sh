#!/bin/bash

# Configuration
# Endpoint: /local-receipts/ingest (Simulates full ingestion with user)
# Endpoint: /api/parsers/legacy/parse (Ad-hoc parsing, no user required)
BASE_URL="http://localhost:8081"
API_ENDPOINT="/local-receipts/ingest"
FULL_URL="${BASE_URL}${API_ENDPOINT}"
RECEIPTS_DIR="receipts"

# User Details (Required for ingestion)
USER_EMAIL="pekelund@gmail.com"
USER_NAME="par"

# Dynamic User ID Retrieval
FIRESTORE_HOST="localhost:8085"
PROJECT_ID="pklnd-local"
DATABASE_ID="(default)"

echo "Fetching User ID for $USER_EMAIL from Firestore Emulator..."
USER_ID_RESPONSE=$(curl -s "http://$FIRESTORE_HOST/v1/projects/$PROJECT_ID/databases/$DATABASE_ID/documents/users")

# Parse the document name (projects/.../users/{id}) to get the ID
USER_ID_PATH=$(echo "$USER_ID_RESPONSE" | jq -r --arg email "$USER_EMAIL" '.documents[] | select(.fields.email.stringValue == $email) | .name')

if [ -z "$USER_ID_PATH" ] || [ "$USER_ID_PATH" == "null" ]; then
    echo "Error: User with email $USER_EMAIL not found in Firestore."
    echo "Make sure the web application is running and you have registered/logged in."
    # Fallback to hardcoded ID if needed, or exit
    exit 1
fi

USER_ID=$(basename "$USER_ID_PATH")
echo "Resolved User ID: $USER_ID"

# Check if receipts directory exists
if [ ! -d "$RECEIPTS_DIR" ]; then
    echo "Error: Directory '$RECEIPTS_DIR' not found."
    echo "Please run this script from the 'local' directory where the 'receipts' folder is located."
    exit 1
fi

echo "Starting receipt ingestion..."
echo "Target URL: $FULL_URL"
echo "User: $USER_NAME ($USER_ID)"
echo "Directory: $RECEIPTS_DIR"
echo "----------------------------------------"

# Loop through all PDF files in the directory
count=0
for file in "$RECEIPTS_DIR"/*.pdf; do
    # Check if file exists (handles case where no PDFs match)
    if [ ! -e "$file" ]; then
        echo "No PDF files found in $RECEIPTS_DIR."
        break
    fi

    filename=$(basename "$file")
    echo "Ingesting: $filename"

    # Send the file using curl
    # -s: Silent mode (don't show progress meter)
    # -w: Write out the HTTP status code
    # -o: Write output to a temporary file
    response_body=$(mktemp)
    http_code=$(curl -s -X POST \
        -F "file=@$file" \
        -F "userId=$USER_ID" \
        -F "userEmail=$USER_EMAIL" \
        -F "userName=$USER_NAME" \
        -w "%{http_code}" \
        -o "$response_body" \
        "$FULL_URL")

    if [ "$http_code" -eq 200 ]; then
        echo "✅ Success (HTTP 200)"
        # Optional: Print the bucket and object name from response
        # cat "$response_body" | jq .
    else
        echo "❌ Failed (HTTP $http_code)"
        echo "Response:"
        cat "$response_body"
    fi
    
    rm "$response_body"
    echo "----------------------------------------"
    ((count++))
done

echo "Finished processing $count files."
