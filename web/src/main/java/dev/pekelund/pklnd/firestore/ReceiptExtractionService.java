package dev.pekelund.pklnd.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptOwnerMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReceiptExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptExtractionService.class);

    private final FirestoreProperties properties;
    private final Optional<Firestore> firestore;

    public ReceiptExtractionService(FirestoreProperties properties, ObjectProvider<Firestore> firestoreProvider) {
        this.properties = properties;
        this.firestore = Optional.ofNullable(firestoreProvider.getIfAvailable());
    }

    public boolean isEnabled() {
        return firestore.isPresent();
    }

    public List<ParsedReceipt> listReceiptsForOwner(ReceiptOwner owner) {
        return listReceipts(owner, false);
    }

    public List<ParsedReceipt> listAllReceipts() {
        return listReceipts(null, true);
    }

    private List<ParsedReceipt> listReceipts(ReceiptOwner owner, boolean includeAllOwners) {
        if (firestore.isEmpty()) {
            return List.of();
        }

        if (!includeAllOwners && owner == null) {
            return List.of();
        }

        try {
            ApiFuture<QuerySnapshot> future = firestore.get()
                .collection(properties.getReceiptsCollection())
                .get();
            QuerySnapshot snapshot = future.get();
            List<ParsedReceipt> receipts = new ArrayList<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                ParsedReceipt parsed = toParsedReceipt(document);
                if (parsed == null) {
                    continue;
                }
                if (!includeAllOwners && !ReceiptOwnerMatcher.belongsToCurrentOwner(parsed.owner(), owner)) {
                    continue;
                }
                receipts.add(parsed);
            }
            receipts.sort(Comparator.comparing(ParsedReceipt::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
            return Collections.unmodifiableList(receipts);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading parsed receipts from Firestore", ex);
            throw new ReceiptExtractionAccessException("Interrupted while loading parsed receipts from Firestore.", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to load parsed receipts from Firestore", ex);
            throw new ReceiptExtractionAccessException("Failed to load parsed receipts from Firestore.", ex);
        }
    }

    public Optional<ParsedReceipt> findById(String id) {
        if (firestore.isEmpty() || !StringUtils.hasText(id)) {
            return Optional.empty();
        }

        try {
            DocumentReference reference = firestore.get()
                .collection(properties.getReceiptsCollection())
                .document(id);
            DocumentSnapshot snapshot = reference.get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(toParsedReceipt(snapshot));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading parsed receipt {} from Firestore", id, ex);
            throw new ReceiptExtractionAccessException("Interrupted while loading parsed receipt from Firestore.", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to load parsed receipt {} from Firestore", id, ex);
            throw new ReceiptExtractionAccessException("Failed to load parsed receipt from Firestore.", ex);
        }
    }

    public void deleteReceiptsForOwner(ReceiptOwner owner) {
        if (owner == null || firestore.isEmpty()) {
            return;
        }

        try {
            Iterable<DocumentReference> documents = firestore.get()
                .collection(properties.getReceiptsCollection())
                .listDocuments();
            for (DocumentReference document : documents) {
                DocumentSnapshot snapshot = document.get().get();
                if (!snapshot.exists()) {
                    continue;
                }
                ParsedReceipt receipt = toParsedReceipt(snapshot);
                if (receipt == null) {
                    continue;
                }
                if (!ReceiptOwnerMatcher.belongsToCurrentOwner(receipt.owner(), owner)) {
                    continue;
                }
                document.delete().get();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while deleting parsed receipts from Firestore", ex);
            throw new ReceiptExtractionAccessException("Interrupted while deleting parsed receipts from Firestore.", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to delete parsed receipts from Firestore", ex);
            throw new ReceiptExtractionAccessException("Failed to delete parsed receipts from Firestore.", ex);
        }
    }

    private ParsedReceipt toParsedReceipt(DocumentSnapshot snapshot) {
        Map<String, Object> data = snapshot.getData();
        if (data == null || data.isEmpty()) {
            return null;
        }

        String bucket = asString(data.get("bucket"));
        String objectName = asString(data.get("objectName"));
        String objectPath = asString(data.get("objectPath"));
        String status = asString(data.get("status"));
        String statusMessage = asString(data.get("statusMessage"));
        String rawResponse = asString(data.get("rawResponse"));
        String error = asString(data.get("error"));

        Instant updatedAt = extractUpdatedAt(snapshot, data.get("updatedAt"));
        ReceiptOwner owner = toReceiptOwner(data.get("owner"));

        Map<String, Object> structuredData = toStringObjectMap(data.get("data"));
        Map<String, Object> general = toStringObjectMap(structuredData.get("general"));
        List<Map<String, Object>> items = toMapList(structuredData.get("items"));
        List<Map<String, Object>> vats = toMapList(structuredData.get("vats"));
        List<Map<String, Object>> generalDiscounts = toMapList(structuredData.get("generalDiscounts"));
        List<Map<String, Object>> errors = toMapList(structuredData.get("errors"));
        String rawText = asString(structuredData.get("rawText"));

        return new ParsedReceipt(
            snapshot.getId(),
            bucket,
            objectName,
            objectPath,
            owner,
            status,
            statusMessage,
            updatedAt,
            general,
            items,
            vats,
            generalDiscounts,
            errors,
            rawText,
            rawResponse,
            error
        );
    }

    private Instant extractUpdatedAt(DocumentSnapshot snapshot, Object fallbackValue) {
        Timestamp timestamp = snapshot.getTimestamp("updatedAt");
        if (timestamp == null && fallbackValue instanceof Timestamp fallbackTimestamp) {
            timestamp = fallbackTimestamp;
        }
        if (timestamp == null) {
            return null;
        }
        return timestamp.toDate().toInstant();
    }

    private ReceiptOwner toReceiptOwner(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        String id = asString(map.get("id"));
        String displayName = asString(map.get("displayName"));
        String email = asString(map.get("email"));
        ReceiptOwner owner = new ReceiptOwner(id, displayName, email);
        return owner.hasValues() ? owner : null;
    }

    private Map<String, Object> toStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) {
                continue;
            }
            result.put(key.toString(), entry.getValue());
        }
        return result;
    }

    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : list) {
            Map<String, Object> mapped = toStringObjectMap(element);
            if (!mapped.isEmpty()) {
                result.add(mapped);
            } else if (element instanceof Map<?, ?> mapElement && mapElement.isEmpty()) {
                result.add(Map.of());
            }
        }
        return List.copyOf(result);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return StringUtils.hasText(string) ? string : null;
        }
        return value.toString();
    }
}
