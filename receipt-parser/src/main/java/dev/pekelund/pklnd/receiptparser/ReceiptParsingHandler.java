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

        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectName)) {
            LOGGER.warn("Storage event missing bucket ({}) or object name ({})", bucket, objectName);
            return;
        }

        Blob blob = storage.get(BlobId.of(bucket, objectName));
        if (blob == null) {
            LOGGER.warn("Blob not found for gs://{}/{}", bucket, objectName);
            return;
        }

        ReceiptOwner owner = ReceiptOwner.fromMetadata(blob.getMetadata());
        repository.markStatus(bucket, objectName, owner, ReceiptProcessingStatus.RECEIVED, "Storage event received");
        blob = updateProcessingMetadata(blob, ReceiptProcessingStatus.RECEIVED, "Storage event received");

        try {
            repository.markStatus(bucket, objectName, owner, ReceiptProcessingStatus.PARSING, "Receipt parsing started");
            blob = updateProcessingMetadata(blob, ReceiptProcessingStatus.PARSING, "Receipt parsing started");

            if (!isPdf(blob)) {
                String message = "Only PDF receipts are processed";
                repository.markStatus(bucket, objectName, owner, ReceiptProcessingStatus.SKIPPED, message);
                blob = updateProcessingMetadata(blob, ReceiptProcessingStatus.SKIPPED, message);
                return;
            }

            byte[] pdfBytes = blob.getContent();
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
            repository.saveExtraction(bucket, objectName, owner, extractionResult, "Receipt parsing completed");
            updateProcessingMetadata(blob, ReceiptProcessingStatus.COMPLETED, "Receipt parsing completed");
            LOGGER.info("ReceiptParsingHandler successfully completed extraction for gs://{}/{}", bucket, objectName);
        } catch (ReceiptParsingException ex) {
            LOGGER.error("Receipt parsing failed for gs://{}/{}", bucket, objectName, ex);
            repository.markFailure(bucket, objectName, owner, "Receipt parsing failed", ex);
            updateProcessingMetadata(blob, ReceiptProcessingStatus.FAILED, "Receipt parsing failed");
            throw ex;
        } catch (RuntimeException ex) {
            LOGGER.error("Unexpected error while parsing receipt gs://{}/{}", bucket, objectName, ex);
            repository.markFailure(bucket, objectName, owner, "Unexpected error during receipt parsing", ex);
            updateProcessingMetadata(blob, ReceiptProcessingStatus.FAILED, "Unexpected error during receipt parsing");
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

    private Blob updateProcessingMetadata(Blob blob, ReceiptProcessingStatus status, String message) {
        Map<String, String> metadata = new HashMap<>(Optional.ofNullable(blob.getMetadata()).orElse(Map.of()));
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
