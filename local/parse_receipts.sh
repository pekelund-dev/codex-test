#!/bin/bash

# Configuration
# Endpoint: /local-receipts/ingest (Simulates full ingestion with user)
# Endpoint: /api/parsers/legacy/parse (Ad-hoc parsing, no user required)
BASE_URL="http://localhost:8081"
API_ENDPOINT="/local-receipts/ingest"
FULL_URL="${BASE_URL}${API_ENDPOINT}"
RECEIPTS_DIR="receipts"

# User Details (Required for ingestion)
USER_ID="zRiQY1MMPckvzqRXcPsj"
USER_EMAIL="pekelund@gmail.com"
USER_NAME="par"

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
