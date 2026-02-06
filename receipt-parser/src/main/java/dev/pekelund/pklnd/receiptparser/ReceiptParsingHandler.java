package dev.pekelund.pklnd.receiptparser;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Handles the business logic for parsing receipt files.
 */
public class ReceiptParsingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptParsingHandler.class);

    private static final String METADATA_STATUS = "receipt.processing.status";
    private static final String METADATA_MESSAGE = "receipt.processing.message";
    private static final String METADATA_UPDATED = "receipt.processing.updatedAt";
    private static final String METADATA_REPARSE_REQUESTED = "receipt.reparse.requested";

    private final Storage storage;
    private final ReceiptExtractionRepository repository;
    private final ReceiptDataExtractor extractor;

    public ReceiptParsingHandler(Storage storage, ReceiptExtractionRepository repository,
        ReceiptDataExtractor extractor) {
        this.storage = storage;
        this.repository = repository;
        this.extractor = extractor;
        LOGGER.info("Constructing ReceiptParsingHandler with storage {}, repository {}, extractor instance id {}",
            storage.getClass().getName(), repository.getClass().getName(), System.identityHashCode(extractor));
    }

    public void handle(StorageObjectEvent storageObjectEvent) {
        LOGGER.info("ReceiptParsingHandler invoked with event {}", storageObjectEvent);
        if (storageObjectEvent == null) {
            LOGGER.warn("Received null storage event data");
            return;
        }

        String bucket = storageObjectEvent.getBucket();
        String objectName = storageObjectEvent.getName();

        LOGGER.info("ReceiptParsingHandler processing object gs://{}/{} with extractor instance id {}", bucket, objectName,
            System.identityHashCode(extractor));
        ReceiptProcessingMdc.setStage("VALIDATE_EVENT");

        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectName)) {
            LOGGER.warn("Storage event missing bucket ({}) or object name ({})", bucket, objectName);
            return;
        }

        ReceiptProcessingMdc.setStage("FETCH_BLOB");
        LOGGER.info("Fetching blob metadata from Cloud Storage for gs://{}/{}", bucket, objectName);
        Blob blob = storage.get(BlobId.of(bucket, objectName));
        if (blob == null) {
            LOGGER.warn("Blob not found for gs://{}/{}", bucket, objectName);
            return;
        }

        ReceiptProcessingMdc.setStage("MERGE_METADATA");
        Map<String, String> metadata = new HashMap<>(Optional.ofNullable(blob.getMetadata()).orElse(Map.of()));
        Map<String, String> eventMetadata = storageObjectEvent.getMetadata();
        if (eventMetadata != null && !eventMetadata.isEmpty()) {
            metadata.putAll(eventMetadata);
        }
        LOGGER.info("Merged metadata for gs://{}/{} (blob keys: {}, event keys: {}, combined keys: {})", bucket, objectName,
            blob.getMetadata() != null ? blob.getMetadata().size() : 0,
            eventMetadata != null ? eventMetadata.size() : 0, metadata.size());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Combined metadata for gs://{}/{}: {}", bucket, objectName, metadata);
        }

        boolean reparseRequested = isReparseRequested(metadata);

        ReceiptOwner owner = ReceiptOwner.fromMetadata(metadata);
        if (owner == null) {
            ReceiptOwner fallbackOwner = storageObjectEvent.resolveOwner();
            if (fallbackOwner != null) {
                owner = fallbackOwner;
                metadata.putAll(fallbackOwner.toMetadata());
            }
        }
        if (owner != null) {
            metadata.putAll(owner.toMetadata());
            LOGGER.info("Resolved receipt owner for gs://{}/{} -> id={} email={}", bucket, objectName, owner.id(), owner.email());
        } else {
            LOGGER.warn("No owner metadata found for gs://{}/{}; receipt will be stored without owner linkage", bucket,
                objectName);
        }
        ReceiptProcessingMdc.attachOwner(owner);

        ReceiptProcessingMdc.setStage("STATUS_RECEIVED");
        repository.markStatus(bucket, objectName, owner, ReceiptProcessingStatus.RECEIVED, "Storage event received");
        blob = updateProcessingMetadata(blob, ReceiptProcessingStatus.RECEIVED, "Storage event received", metadata);

        try {
            ReceiptProcessingMdc.setStage("STATUS_PARSING");
            repository.markStatus(bucket, objectName, owner, ReceiptProcessingStatus.PARSING, "Receipt parsing started");
            blob = updateProcessingMetadata(blob, ReceiptProcessingStatus.PARSING, "Receipt parsing started", metadata);

            if (!isPdf(blob)) {
                String message = "Only PDF receipts are processed";
                ReceiptProcessingMdc.setStage("STATUS_SKIPPED");
                repository.markStatus(bucket, objectName, owner, ReceiptProcessingStatus.SKIPPED, message);
                blob = updateProcessingMetadata(blob, ReceiptProcessingStatus.SKIPPED, message, metadata);
                return;
            }

            ReceiptProcessingMdc.setStage("READ_CONTENT");
            byte[] pdfBytes = blob.getContent();
            LOGGER.info("Downloaded {} bytes for gs://{}/{}", pdfBytes != null ? pdfBytes.length : 0, bucket, objectName);
            ReceiptProcessingMdc.setStage("EXTRACT");
            ReceiptExtractionResult extractionResult = extractor.extract(pdfBytes, blob.getName());
            int itemsCount = countItems(extractionResult);
            int topLevelKeys = extractionResult.structuredData() != null
                ? extractionResult.structuredData().size()
                : 0;
            int rawResponseLength = extractionResult.rawResponse() != null
                ? extractionResult.rawResponse().length()
                : 0;
            LOGGER.info("ReceiptParsingHandler extracted {} top-level fields and {} items (raw response length {} characters) for gs://{}/{}",
                topLevelKeys, itemsCount, rawResponseLength, bucket, objectName);
            ReceiptProcessingMdc.setStage("STATUS_COMPLETED");
            repository.saveExtraction(bucket, objectName, owner, extractionResult, "Receipt parsing completed", reparseRequested);
            updateProcessingMetadata(blob, ReceiptProcessingStatus.COMPLETED, "Receipt parsing completed", metadata);
            LOGGER.info("ReceiptParsingHandler successfully completed extraction for gs://{}/{}", bucket, objectName);
            ReceiptProcessingMdc.setStage("DONE");
        } catch (ReceiptParsingException ex) {
            ReceiptProcessingMdc.setStage("FAILED");
            LOGGER.error("Receipt parsing failed for gs://{}/{}", bucket, objectName, ex);
            repository.markFailure(bucket, objectName, owner, "Receipt parsing failed", ex);
            updateProcessingMetadata(blob, ReceiptProcessingStatus.FAILED, "Receipt parsing failed", metadata);
            throw ex;
        } catch (RuntimeException ex) {
            ReceiptProcessingMdc.setStage("FAILED");
            LOGGER.error("Unexpected error while parsing receipt gs://{}/{}", bucket, objectName, ex);
            repository.markFailure(bucket, objectName, owner, "Unexpected error during receipt parsing", ex);
            updateProcessingMetadata(blob, ReceiptProcessingStatus.FAILED, "Unexpected error during receipt parsing",
                metadata);
            throw ex;
        }
    }

    private boolean isPdf(Blob blob) {
        String contentType = blob.getContentType();
        if (StringUtils.hasText(contentType)) {
            String normalized = contentType.toLowerCase(Locale.US);
            if (normalized.equals("application/pdf") || normalized.startsWith("application/pdf")) {
                return true;
            }
        }
        String name = blob.getName();
        return name != null && name.toLowerCase(Locale.US).endsWith(".pdf");
    }

    private boolean isReparseRequested(Map<String, String> metadata) {
        if (metadata == null) {
            return false;
        }
        String value = metadata.get(METADATA_REPARSE_REQUESTED);
        return value != null && Boolean.parseBoolean(value);
    }

    private Blob updateProcessingMetadata(Blob blob, ReceiptProcessingStatus status, String message,
        Map<String, String> metadata) {
        metadata.put(METADATA_STATUS, status.name());
        metadata.put(METADATA_MESSAGE, message);
        metadata.put(METADATA_UPDATED, Instant.now().toString());
        return blob.toBuilder().setMetadata(metadata).build().update();
    }

    private int countItems(ReceiptExtractionResult extractionResult) {
        if (extractionResult == null || extractionResult.structuredData() == null) {
            return 0;
        }
        Object items = extractionResult.structuredData().get("items");
        if (items instanceof Collection<?> collection) {
            return collection.size();
        }
        if (items instanceof Map<?, ?> map) {
            return map.size();
        }
        return items != null ? 1 : 0;
    }
}
