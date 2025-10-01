package dev.pekelund.responsiveauth.function;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import dev.pekelund.responsiveauth.storage.ReceiptOwner;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Persists receipt extraction results and status updates in Firestore.
 */
public class ReceiptExtractionRepository {

    private final Firestore firestore;
    private final String collectionName;

    public ReceiptExtractionRepository(Firestore firestore, String collectionName) {
        this.firestore = firestore;
        this.collectionName = collectionName;
    }

    public void markStatus(String bucket, String objectName, ReceiptOwner owner,
        ReceiptProcessingStatus status, String message) {
        updateDocument(bucket, objectName, owner, status, message, null, null);
    }

    public void saveExtraction(String bucket, String objectName, ReceiptOwner owner,
        ReceiptExtractionResult extractionResult, String message) {
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
                .document(buildDocumentId(bucket, objectName));
            documentReference.set(payload, SetOptions.merge()).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ReceiptParsingException("Interrupted while writing receipt data to Firestore", ex);
        } catch (ExecutionException ex) {
            throw new ReceiptParsingException("Failed to store receipt data in Firestore", ex);
        }
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
