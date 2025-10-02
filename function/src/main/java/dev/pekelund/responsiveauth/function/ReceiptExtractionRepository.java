package dev.pekelund.responsiveauth.function;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import dev.pekelund.responsiveauth.storage.ReceiptOwner;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists receipt extraction results and status updates in Firestore.
 */
public class ReceiptExtractionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptExtractionRepository.class);

    private final Firestore firestore;
    private final String collectionName;

    public ReceiptExtractionRepository(Firestore firestore, String collectionName) {
        this.firestore = firestore;
        this.collectionName = collectionName;
        LOGGER.info("ReceiptExtractionRepository initialized with collection '{}'", collectionName);
    }

    public void markStatus(String bucket, String objectName, ReceiptOwner owner,
        ReceiptProcessingStatus status, String message) {
        LOGGER.info("Firestore status update for gs://{}/{} -> {} ({})", bucket, objectName, status, message);
        updateDocument(bucket, objectName, owner, status, message, null, null);
    }

    public void saveExtraction(String bucket, String objectName, ReceiptOwner owner,
        ReceiptExtractionResult extractionResult, String message) {
        int itemCount = extractItemCount(extractionResult);
        LOGGER.info("Persisting extraction for gs://{}/{} with {} items", bucket, objectName, itemCount);
        updateDocument(bucket, objectName, owner, ReceiptProcessingStatus.COMPLETED, message, extractionResult, null);
    }

    public void markFailure(String bucket, String objectName, ReceiptOwner owner, String errorMessage, Throwable error) {
        String detailedMessage = error != null ? error.getMessage() : null;
        String combined = errorMessage;
        if (detailedMessage != null && !detailedMessage.equals(errorMessage)) {
            combined = errorMessage + ": " + detailedMessage;
        }
        updateDocument(bucket, objectName, owner, ReceiptProcessingStatus.FAILED, combined, null, combined);
    }

    private void updateDocument(String bucket, String objectName, ReceiptOwner owner,
        ReceiptProcessingStatus status, String message, ReceiptExtractionResult extractionResult, String errorMessage) {
        String documentId = buildDocumentId(bucket, objectName);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("bucket", bucket);
            payload.put("objectName", objectName);
            payload.put("objectPath", "gs://" + bucket + "/" + objectName);
            payload.put("status", status.name());
            payload.put("statusMessage", message);
            payload.put("updatedAt", Timestamp.now());

            if (owner != null) {
                payload.put("owner", ownerMap(owner));
            }

            if (extractionResult != null) {
                payload.put("data", extractionResult.structuredData());
                payload.put("rawResponse", extractionResult.rawResponse());
            }

            if (errorMessage != null) {
                payload.put("error", errorMessage);
            }

            DocumentReference documentReference = firestore.collection(collectionName)
                .document(documentId);
            LOGGER.info("Writing payload with {} entries to Firestore document {}/{}", payload.size(), collectionName, documentId);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Firestore payload for {}/{}: {}", collectionName, documentId, payload);
            }
            documentReference.set(payload, SetOptions.merge()).get();
            LOGGER.info("Firestore document {}/{} successfully updated", collectionName, documentId);
        } catch (InterruptedException ex) {
            LOGGER.error("Interrupted while writing Firestore document {}/{}", collectionName, documentId, ex);
            Thread.currentThread().interrupt();
            throw new ReceiptParsingException("Interrupted while writing receipt data to Firestore", ex);
        } catch (ExecutionException ex) {
            LOGGER.error("ExecutionException while writing Firestore document {}/{}", collectionName, documentId, ex);
            throw new ReceiptParsingException("Failed to store receipt data in Firestore", ex);
        }
    }

    private int extractItemCount(ReceiptExtractionResult extractionResult) {
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
        return Optional.ofNullable(items)
            .map(Object::toString)
            .filter(str -> !str.isBlank())
            .map(str -> 1)
            .orElse(0);
    }

    private Map<String, Object> ownerMap(ReceiptOwner owner) {
        Map<String, Object> ownerMap = new HashMap<>();
        ownerMap.put("id", owner.id());
        ownerMap.put("displayName", owner.displayName());
        ownerMap.put("email", owner.email());
        return ownerMap;
    }

    private String buildDocumentId(String bucket, String objectName) {
        String value = bucket + ":" + objectName;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
