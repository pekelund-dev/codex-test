package dev.pekelund.pklnd.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import dev.pekelund.pklnd.receipts.ReceiptItemConstants;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReceiptItemService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptItemService.class);

    private final FirestoreProperties properties;
    private final Optional<Firestore> firestore;
    private final FirestoreReadRecorder readRecorder;
    private final String receiptItemsCollection;
    private final String itemStatsCollection;

    public ReceiptItemService(
        FirestoreProperties properties,
        ObjectProvider<Firestore> firestoreProvider,
        FirestoreReadRecorder readRecorder
    ) {
        this.properties = properties;
        this.firestore = Optional.ofNullable(firestoreProvider.getIfAvailable());
        this.readRecorder = readRecorder;
        this.receiptItemsCollection = properties.getReceiptItemsCollection();
        this.itemStatsCollection = properties.getItemStatsCollection();
    }

    public Map<String, Long> loadItemOccurrences(Collection<String> normalizedEans, ReceiptOwner owner,
        boolean includeAllOwners) {

        if (firestore.isEmpty() || normalizedEans == null || normalizedEans.isEmpty()) {
            return Map.of();
        }

        Set<String> distinctEans = normalizedEans.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctEans.isEmpty()) {
            return Map.of();
        }

        String ownerId = includeAllOwners ? ReceiptItemConstants.GLOBAL_OWNER_ID : owner != null ? owner.id() : null;
        if (!includeAllOwners && !StringUtils.hasText(ownerId)) {
            Map<String, Long> empty = new HashMap<>();
            for (String ean : distinctEans) {
                empty.put(ean, 0L);
            }
            return Map.copyOf(empty);
        }

        try {
            Firestore db = firestore.get();
            List<String> docIds = new ArrayList<>();
            Map<String, String> docIdToEan = new HashMap<>();
            for (String ean : distinctEans) {
                String docId = buildStatsDocumentId(ownerId, ean);
                docIds.add(docId);
                docIdToEan.put(docId, ean);
            }

            Map<String, Long> counts = new HashMap<>();
            for (int start = 0; start < docIds.size(); start += 10) {
                int end = Math.min(start + 10, docIds.size());
                List<String> chunk = docIds.subList(start, end);
                if (chunk.isEmpty()) {
                    continue;
                }
                QuerySnapshot snapshot = db.collection(itemStatsCollection)
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .get();
                recordRead("Load item stats for " + chunk.size() + " entries", snapshot != null ? snapshot.size() : 0);
                if (snapshot == null) {
                    continue;
                }
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    String docId = document.getId();
                    String ean = docIdToEan.get(docId);
                    if (!StringUtils.hasText(ean)) {
                        continue;
                    }
                    Long count = document.getLong("count");
                    counts.put(ean, count != null ? count : 0L);
                }
            }

            for (String ean : distinctEans) {
                counts.putIfAbsent(ean, 0L);
            }
            return Map.copyOf(counts);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading item statistics from Firestore", ex);
            throw new ReceiptExtractionAccessException("Interrupted while loading item statistics from Firestore.", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to load item statistics from Firestore", ex);
            throw new ReceiptExtractionAccessException("Failed to load item statistics from Firestore.", ex);
        }
    }

    public List<ReceiptExtractionService.ReceiptItemReference> findReceiptItemReferences(String normalizedEan, ReceiptOwner owner,
        boolean includeAllOwners) {

        if (firestore.isEmpty() || !StringUtils.hasText(normalizedEan)) {
            return List.of();
        }

        String trimmed = normalizedEan.trim();
        try {
            Firestore db = firestore.get();
            Query query = db.collection(receiptItemsCollection)
                .whereEqualTo("normalizedEan", trimmed);
            if (!includeAllOwners) {
                if (owner == null || !StringUtils.hasText(owner.id())) {
                    return List.of();
                }
                query = query.whereEqualTo("ownerId", owner.id());
            }

            QuerySnapshot snapshot = query.get().get();
            recordRead("Load receipt item references for " + trimmed,
                snapshot != null ? snapshot.size() : 0);
            if (snapshot == null) {
                return List.of();
            }

            List<ReceiptExtractionService.ReceiptItemReference> references = new ArrayList<>();
            for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
                String receiptId = document.getString("receiptId");
                if (!StringUtils.hasText(receiptId)) {
                    continue;
                }
                String ownerId = document.getString("ownerId");
                Timestamp updatedAt = document.getTimestamp("receiptUpdatedAt");
                String receiptDate = asString(document.get("receiptDate"));
                String storeName = asString(document.get("receiptStoreName"));
                String displayName = asString(document.get("receiptDisplayName"));
                String objectName = asString(document.get("receiptObjectName"));
                Map<String, Object> itemData = toStringObjectMap(document.get("itemData"));
                references.add(new ReceiptExtractionService.ReceiptItemReference(
                    receiptId,
                    ownerId,
                    toInstant(updatedAt),
                    receiptDate,
                    storeName,
                    displayName,
                    objectName,
                    itemData
                ));
            }
            return List.copyOf(references);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading receipt item references from Firestore", ex);
            throw new ReceiptExtractionAccessException("Interrupted while loading receipt item references from Firestore.", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to load receipt item references from Firestore", ex);
            throw new ReceiptExtractionAccessException("Failed to load receipt item references from Firestore.", ex);
        }
    }

    private String buildStatsDocumentId(String ownerId, String normalizedEan) {
        return ownerId + "#" + normalizedEan;
    }

    private void recordRead(String description, long readUnits) {
        readRecorder.record(description, readUnits);
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toDate().toInstant();
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
}
