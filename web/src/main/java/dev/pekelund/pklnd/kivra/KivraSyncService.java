package dev.pekelund.pklnd.kivra;

import dev.pekelund.pklnd.receipts.ReceiptProcessingClient;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.storage.StoredReceiptReference;
import dev.pekelund.pklnd.storage.UploadResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for synchronizing receipts from Kivra to local storage.
 */
@Service
@ConditionalOnProperty(prefix = "kivra", name = "enabled", havingValue = "true")
public class KivraSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KivraSyncService.class);

    private final KivraClient kivraClient;
    private final Optional<ReceiptStorageService> receiptStorageService;
    private final Optional<ReceiptProcessingClient> receiptProcessingClient;
    private final KivraProperties properties;

    public KivraSyncService(
            KivraClient kivraClient,
            Optional<ReceiptStorageService> receiptStorageService,
            Optional<ReceiptProcessingClient> receiptProcessingClient,
            KivraProperties properties) {
        this.kivraClient = kivraClient;
        this.receiptStorageService = receiptStorageService;
        this.receiptProcessingClient = receiptProcessingClient;
        this.properties = properties;
    }

    /**
     * Synchronize receipts from Kivra to local storage.
     *
     * @param owner The receipt owner for the uploaded receipts
     * @return Result of the synchronization
     */
    public KivraSyncResult syncReceipts(ReceiptOwner owner) {
        LOGGER.info("Starting Kivra sync for owner: {}", owner);

        // Check if storage is available
        if (receiptStorageService.isEmpty() || !receiptStorageService.get().isEnabled()) {
            return KivraSyncResult.failure("Kvittoslagring är inte aktiverad");
        }

        // Authenticate if needed
        if (!kivraClient.isAuthenticated()) {
            try {
                KivraAuthenticationResult authResult = kivraClient.authenticate();
                if (!authResult.success()) {
                    return KivraSyncResult.authenticationPending(authResult.qrCodeData(), authResult.message());
                }
            } catch (KivraAuthenticationException ex) {
                LOGGER.error("Kivra authentication failed", ex);
                return KivraSyncResult.failure("Autentisering misslyckades: " + ex.getMessage());
            }
        }

        // Fetch documents from Kivra
        List<KivraDocument> documents;
        try {
            documents = kivraClient.fetchDocuments(properties.getMaxDocuments());
        } catch (KivraClientException ex) {
            LOGGER.error("Failed to fetch documents from Kivra", ex);
            return KivraSyncResult.failure("Kunde inte hämta dokument: " + ex.getMessage());
        }

        // Filter to PDF receipts if configured
        List<KivraDocument> receiptsToSync = documents.stream()
            .filter(doc -> !properties.isPdfOnly() || doc.isPdf())
            .toList();

        if (receiptsToSync.isEmpty()) {
            LOGGER.info("No receipts found to sync from Kivra");
            return KivraSyncResult.success(0, 0, "Inga nya kvitton hittades");
        }

        // Convert to MultipartFile and upload
        List<MultipartFile> files = new ArrayList<>();
        for (KivraDocument doc : receiptsToSync) {
            files.add(new KivraDocumentMultipartFile(doc));
        }

        UploadResult uploadResult = receiptStorageService.get()
            .uploadFilesWithResults(files, owner);

        // Notify receipt processor if configured
        if (receiptProcessingClient.isPresent() && !uploadResult.uploadedReceipts().isEmpty()) {
            try {
                receiptProcessingClient.get().notifyUploads(uploadResult.uploadedReceipts());
            } catch (Exception ex) {
                LOGGER.warn("Failed to notify receipt processor", ex);
            }
        }

        int successCount = uploadResult.uploadedReceipts().size();
        int failureCount = uploadResult.failures().size();
        String message = String.format("Synkade %d kvitton från Kivra", successCount);
        if (failureCount > 0) {
            message += String.format(" (%d misslyckades eller duplikat)", failureCount);
        }

        LOGGER.info("Kivra sync completed: {} receipts uploaded, {} failures",
            successCount, failureCount);

        return KivraSyncResult.success(successCount, failureCount, message);
    }

    /**
     * Check if Kivra sync is available and configured.
     *
     * @return true if Kivra sync can be used
     */
    public boolean isAvailable() {
        return properties.isEnabled() &&
               receiptStorageService.isPresent() &&
               receiptStorageService.get().isEnabled();
    }

    /**
     * MultipartFile adapter for KivraDocument.
     */
    private static class KivraDocumentMultipartFile implements MultipartFile {
        private final KivraDocument document;

        KivraDocumentMultipartFile(KivraDocument document) {
            this.document = document;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return document.title() + ".pdf";
        }

        @Override
        public String getContentType() {
            return document.contentType();
        }

        @Override
        public boolean isEmpty() {
            return document.content() == null || document.content().length == 0;
        }

        @Override
        public long getSize() {
            return document.content() != null ? document.content().length : 0;
        }

        @Override
        public byte[] getBytes() {
            return document.content();
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(document.content());
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("Transfer to file not supported");
        }
    }
}
