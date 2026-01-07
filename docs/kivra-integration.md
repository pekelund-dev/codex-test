# Kivra Integration

This module provides integration with Kivra, a Swedish digital mailbox service, allowing users to automatically fetch and sync their receipts from Kivra to their pklnd receipt archive.

## Overview

[Kivra](https://www.kivra.com) is a widely-used digital mailbox service in Sweden where businesses send receipts, invoices, and other documents directly to users. This integration enables users to automatically import their Kivra receipts into pklnd without manual downloading and uploading.

## Features

- Automatic fetching of receipts from Kivra digital mailbox
- BankID authentication support
- PDF receipt filtering
- Configurable document limits
- Duplicate detection (reuses existing storage service)
- Automatic receipt processing integration

## Configuration

Enable Kivra integration by setting these environment variables:

```bash
export KIVRA_ENABLED=true
export KIVRA_PERSONAL_NUMBER=your-personnummer
export KIVRA_API_BASE_URL=https://app.kivra.com  # Optional, defaults to app.kivra.com
export KIVRA_MAX_DOCUMENTS=100  # Optional, defaults to 100
export KIVRA_PDF_ONLY=true  # Optional, defaults to true
```

Or configure in `application.yml`:

```yaml
kivra:
  enabled: true
  personal-number: ${KIVRA_PERSONAL_NUMBER}
  api-base-url: https://app.kivra.com
  max-documents: 100
  pdf-only: true
```

## Usage

### Web Interface

When Kivra integration is enabled, a "Synka från Kivra" (Sync from Kivra) button appears on the receipt uploads page (`/receipts/uploads`). Click this button to:

1. Authenticate with Kivra using BankID (if not already authenticated)
2. Fetch receipts from your Kivra mailbox
3. Automatically upload them to your receipt storage
4. Trigger receipt processing for text extraction

### API Endpoints

#### Check Kivra Status

```bash
GET /receipts/kivra/status
```

Returns whether Kivra sync is available and configured.

#### Trigger Sync

```bash
POST /receipts/kivra/sync
```

Initiates a sync operation. Returns success/failure status and count of synced receipts.

## Implementation Details

### Current Implementation

The current implementation uses a **stub client** (`StubKivraClient`) that demonstrates the integration pattern without making actual Kivra API calls. This serves as:

- A reference implementation showing the expected interface
- A testing tool for the integration layer
- Documentation for production implementation requirements

### Production Implementation

For production use, a real Kivra client implementation is needed. The implementation should:

1. **Authentication**: Use BankID (Mobile BankID or QR code) for user authentication
2. **API Integration**: Make authenticated HTTP requests to Kivra's endpoints
3. **Document Fetching**: Download receipts and other documents from the user's mailbox
4. **Session Management**: Handle authentication tokens and session refresh

### Reference Implementations

Several open-source tools demonstrate Kivra integration:

- **[kivra-sync](https://github.com/felixandersen/kivra-sync)**: Full-featured tool for syncing Kivra documents with BankID authentication
- **[fetch-kivra](https://github.com/stefangorling/fetch-kivra)**: Python script for downloading Kivra documents

These tools can serve as references for implementing a production Kivra client.

### Architecture

The Kivra integration follows the application's modular architecture:

```
kivra/
├── KivraProperties.java          # Configuration properties
├── KivraClient.java              # Client interface
├── StubKivraClient.java          # Stub implementation (development)
├── KivraDocument.java            # Document model
├── KivraSyncService.java         # Sync orchestration
├── KivraSyncResult.java          # Sync result model
├── KivraController.java          # REST endpoints
├── KivraAuthenticationResult.java # Auth result model
├── KivraClientException.java     # Exception classes
└── package-info.java             # Spring Modulith module definition
```

The module integrates with:
- **storage**: For uploading fetched receipts
- **receipts**: For triggering receipt processing

## Security Considerations

1. **Personal Numbers**: Store the personal number securely using environment variables or secret management
2. **Authentication**: BankID provides strong authentication for accessing Kivra
3. **Session Management**: Implement secure token storage and refresh mechanisms
4. **API Keys**: If Kivra provides API keys, store them securely
5. **Data Privacy**: Handle downloaded documents according to GDPR requirements

## API Documentation

Kivra's official API documentation is available at:
- [Kivra Developer Portal](https://developer.kivra.com/)
- [Kivra Receipt API](http://developer.kivra.com/receipt/posapi/index.html)

Note: The official API is primarily for **sending** documents to Kivra. For **fetching** user documents, you may need to use unofficial approaches or contact Kivra for partner API access.

## Limitations

- Kivra does not officially provide a consumer API for fetching documents
- The current implementation uses a stub client for demonstration
- Production use requires implementing actual Kivra integration
- BankID authentication is required (Swedish personal ID system)
- Only works for users with Swedish Kivra accounts

## Future Enhancements

- Implement production Kivra client with BankID authentication
- Add scheduling for automatic periodic syncs
- Support for non-receipt documents
- Enhanced error handling and retry logic
- Progress tracking for large syncs
- Document type filtering
- Date range filtering for historical receipts

## Testing

Run the Kivra integration tests:

```bash
./mvnw -Pinclude-web -pl web test -Dtest=KivraIntegrationTest
```

The tests verify that:
- Configuration is loaded correctly
- Components are properly wired
- The module follows Spring Modulith boundaries

## Troubleshooting

### Kivra sync button doesn't appear

- Check that `KIVRA_ENABLED=true` is set
- Verify that receipt storage is enabled
- Check application logs for configuration errors

### Sync fails with "Not authenticated"

- Ensure `KIVRA_PERSONAL_NUMBER` is configured
- Check that BankID authentication is working
- Verify network connectivity to Kivra

### No receipts are synced

- Verify that your Kivra mailbox contains receipts
- Check the `KIVRA_MAX_DOCUMENTS` limit
- Enable debug logging: `logging.level.dev.pekelund.pklnd.kivra=DEBUG`

## License

This integration is part of the pklnd project and follows the same license terms.
