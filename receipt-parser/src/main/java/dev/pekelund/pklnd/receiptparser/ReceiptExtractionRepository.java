package dev.pekelund.pklnd.receiptparser;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import dev.pekelund.pklnd.receipts.ReceiptItemConstants;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Persists receipt extraction results and status updates in Firestore.
 */
public class ReceiptExtractionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptExtractionRepository.class);
    private static final Pattern EAN_PATTERN = Pattern.compile("(\\d{8,14})");

    private final Firestore firestore;
    private final String collectionName;
    private final String itemsCollectionName;
    private final String itemStatsCollectionName;

    public ReceiptExtractionRepository(Firestore firestore, String collectionName,
        String itemsCollectionName, String itemStatsCollectionName) {

        this.firestore = Objects.requireNonNull(firestore, "firestore");
        this.collectionName = Objects.requireNonNull(collectionName, "collectionName");
        this.itemsCollectionName = Objects.requireNonNull(itemsCollectionName, "itemsCollectionName");
        this.itemStatsCollectionName = Objects.requireNonNull(itemStatsCollectionName, "itemStatsCollectionName");
        LOGGER.info("ReceiptExtractionRepository initialized with collections receipts='{}', items='{}', stats='{}'",
            collectionName, itemsCollectionName, itemStatsCollectionName);
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
        Timestamp updateTimestamp = Timestamp.now();

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("bucket", bucket);
            payload.put("objectName", objectName);
            payload.put("objectPath", "gs://" + bucket + "/" + objectName);
            payload.put("status", status.name());
            payload.put("statusMessage", message);
            payload.put("updatedAt", updateTimestamp);

            if (owner != null) {
                payload.put("owner", ownerMap(owner));
            }

            Map<String, Object> structuredData = extractionResult != null ? extractionResult.structuredData() : null;
            Map<String, Object> general = extractGeneral(structuredData);
            List<Map<String, Object>> items = extractItems(structuredData);

            if (extractionResult != null) {
                payload.put("data", structuredData);
                payload.put("rawResponse", extractionResult.rawResponse());
            }

            if (errorMessage != null) {
                payload.put("error", errorMessage);
            }

            DocumentReference documentReference = firestore.collection(collectionName)
                .document(documentId);
            LOGGER.info("Writing payload with {} entries to Firestore document {}/{}", payload.size(), collectionName,
                documentId);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Firestore payload for {}/{}: {}", collectionName, documentId, payload);
            }

            ItemSyncPlan syncPlan = determineSyncPlan(documentId, owner, status, general, items, updateTimestamp);

            WriteBatch batch = firestore.batch();
            batch.set(documentReference, payload, SetOptions.merge());
            applyItemSyncPlan(batch, syncPlan, updateTimestamp);

            batch.commit().get();
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

    private ItemSyncPlan determineSyncPlan(String documentId, ReceiptOwner owner, ReceiptProcessingStatus status,
        Map<String, Object> general, List<Map<String, Object>> items, Timestamp updatedAt)
        throws InterruptedException, ExecutionException {

        if (status == ReceiptProcessingStatus.COMPLETED) {
            return buildUpsertPlan(documentId, owner, general, items, updatedAt);
        }
        if (status == ReceiptProcessingStatus.FAILED || status == ReceiptProcessingStatus.SKIPPED) {
            return buildRemovalPlan(documentId);
        }
        return ItemSyncPlan.empty();
    }

    private ItemSyncPlan buildRemovalPlan(String documentId) throws InterruptedException, ExecutionException {
        QuerySnapshot snapshot = firestore.collection(itemsCollectionName)
            .whereEqualTo("receiptId", documentId)
            .get()
            .get();

        if (snapshot == null || snapshot.isEmpty()) {
            return ItemSyncPlan.empty();
        }

        List<DocumentReference> deletions = new ArrayList<>();
        Map<StatsKey, Long> previousCounts = new HashMap<>();
        for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
            deletions.add(doc.getReference());
            accumulateExistingCounts(doc, previousCounts);
        }

        Map<StatsKey, Long> deltas = new HashMap<>();
        for (Map.Entry<StatsKey, Long> entry : previousCounts.entrySet()) {
            deltas.put(entry.getKey(), -entry.getValue());
        }
        return new ItemSyncPlan(deletions, List.of(), deltas, Map.of());
    }

    private ItemSyncPlan buildUpsertPlan(String documentId, ReceiptOwner owner, Map<String, Object> general,
        List<Map<String, Object>> items, Timestamp updatedAt) throws InterruptedException, ExecutionException {

        QuerySnapshot snapshot = firestore.collection(itemsCollectionName)
            .whereEqualTo("receiptId", documentId)
            .get()
            .get();

        List<DocumentReference> deletions = new ArrayList<>();
        Map<StatsKey, Long> previousCounts = new HashMap<>();
        if (snapshot != null) {
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                deletions.add(doc.getReference());
                accumulateExistingCounts(doc, previousCounts);
            }
        }

        List<ItemWrite> writes = new ArrayList<>();
        Map<StatsKey, Long> newCounts = new HashMap<>();
        Map<StatsKey, StatsMetadata> metadata = new HashMap<>();
        String ownerId = owner != null ? owner.id() : null;
        String receiptDate = asString(general.get("receiptDate"));
        String storeName = asString(general.get("storeName"));

        for (int index = 0; index < items.size(); index++) {
            Map<String, Object> item = items.get(index);
            String normalizedEan = extractNormalizedEan(item);
            if (!StringUtils.hasText(normalizedEan)) {
                continue;
            }

            Map<String, Object> document = new LinkedHashMap<>();
            document.put("receiptId", documentId);
            document.put("normalizedEan", normalizedEan);
            document.put("itemIndex", index);
            document.put("itemData", item);
            document.put("receiptUpdatedAt", updatedAt);
            if (StringUtils.hasText(receiptDate)) {
                document.put("receiptDate", receiptDate);
            }
            if (StringUtils.hasText(storeName)) {
                document.put("receiptStoreName", storeName);
            }
            if (StringUtils.hasText(ownerId)) {
                document.put("ownerId", ownerId);
            }

            DocumentReference reference = firestore.collection(itemsCollectionName).document();
            writes.add(new ItemWrite(reference, document));

            if (StringUtils.hasText(ownerId)) {
                StatsKey ownerKey = new StatsKey(ownerId, normalizedEan);
                newCounts.merge(ownerKey, 1L, Long::sum);
                metadata.put(ownerKey, new StatsMetadata(documentId, receiptDate, storeName, updatedAt));
            }

            StatsKey globalKey = new StatsKey(ReceiptItemConstants.GLOBAL_OWNER_ID, normalizedEan);
            newCounts.merge(globalKey, 1L, Long::sum);
            metadata.put(globalKey, new StatsMetadata(documentId, receiptDate, storeName, updatedAt));
        }

        Map<StatsKey, Long> deltas = new HashMap<>();
        for (Map.Entry<StatsKey, Long> entry : previousCounts.entrySet()) {
            long current = newCounts.getOrDefault(entry.getKey(), 0L);
            long delta = current - entry.getValue();
            if (delta != 0) {
                deltas.put(entry.getKey(), delta);
            }
        }
        for (Map.Entry<StatsKey, Long> entry : newCounts.entrySet()) {
            if (!previousCounts.containsKey(entry.getKey())) {
                deltas.put(entry.getKey(), entry.getValue());
            }
        }

        return new ItemSyncPlan(deletions, writes, deltas, metadata);
    }

    private void applyItemSyncPlan(WriteBatch batch, ItemSyncPlan plan, Timestamp updatedAt) {
        if (plan == null || plan.isEmpty()) {
            return;
        }
        for (DocumentReference reference : plan.deletions()) {
            batch.delete(reference);
        }
        for (ItemWrite write : plan.writes()) {
            batch.set(write.reference(), write.data(), SetOptions.merge());
        }
        for (Map.Entry<StatsKey, Long> entry : plan.deltas().entrySet()) {
            StatsKey key = entry.getKey();
            long delta = entry.getValue();
            if (delta == 0 || !StringUtils.hasText(key.ownerId()) || !StringUtils.hasText(key.normalizedEan())) {
                continue;
            }
            Map<String, Object> updates = buildStatsUpdate(key, delta, updatedAt, plan.metadata().get(key));
            DocumentReference statsRef = firestore.collection(itemStatsCollectionName)
                .document(buildStatsDocumentId(key.ownerId(), key.normalizedEan()));
            batch.set(statsRef, updates, SetOptions.merge());
        }
    }

    private Map<String, Object> buildStatsUpdate(StatsKey key, long delta, Timestamp updatedAt, StatsMetadata metadata) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("ownerId", key.ownerId());
        updates.put("normalizedEan", key.normalizedEan());
        updates.put("updatedAt", updatedAt);
        updates.put("count", FieldValue.increment(delta));
        if (delta > 0 && metadata != null) {
            if (StringUtils.hasText(metadata.receiptId())) {
                updates.put("lastReceiptId", metadata.receiptId());
            }
            if (StringUtils.hasText(metadata.receiptDate())) {
                updates.put("lastReceiptDate", metadata.receiptDate());
            }
            if (StringUtils.hasText(metadata.storeName())) {
                updates.put("lastStoreName", metadata.storeName());
            }
            updates.put("lastUpdatedAt", metadata.updatedAt());
        }
        return updates;
    }

    private void accumulateExistingCounts(DocumentSnapshot document, Map<StatsKey, Long> counts) {
        String ownerId = document.getString("ownerId");
        String normalizedEan = document.getString("normalizedEan");
        if (!StringUtils.hasText(normalizedEan)) {
            return;
        }
        if (StringUtils.hasText(ownerId)) {
            counts.merge(new StatsKey(ownerId, normalizedEan), 1L, Long::sum);
        }
        counts.merge(new StatsKey(ReceiptItemConstants.GLOBAL_OWNER_ID, normalizedEan), 1L, Long::sum);
    }

    private Map<String, Object> extractGeneral(Map<String, Object> structuredData) {
        if (structuredData == null) {
            return Map.of();
        }
        Object general = structuredData.get("general");
        return toStringObjectMap(general);
    }

    private List<Map<String, Object>> extractItems(Map<String, Object> structuredData) {
        if (structuredData == null) {
            return List.of();
        }
        Object items = structuredData.get("items");
        if (!(items instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : collection) {
            if (element instanceof Map<?, ?> map) {
                result.add(toStringObjectMap(map));
            }
        }
        return List.copyOf(result);
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
        return Collections.unmodifiableMap(result);
    }

    private String extractNormalizedEan(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String[] keys = {"ean", "gtin", "barcode", "code", "productCode", "itemCode"};
        for (String key : keys) {
            String normalized = normalizeEanValue(item.get(key));
            if (normalized != null) {
                return normalized;
            }
        }
        return normalizeEanValue(item.get("name"));
    }

    private String normalizeEanValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        Matcher matcher = EAN_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String digitsOnly = text.replaceAll("\\D+", "");
        if (isValidEan(digitsOnly)) {
            return digitsOnly;
        }
        if (text.chars().allMatch(Character::isDigit) && isValidEan(text)) {
            return text;
        }
        return null;
    }

    private boolean isValidEan(String digits) {
        return StringUtils.hasText(digits) && digits.length() >= 8 && digits.length() <= 14;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return StringUtils.hasText(string) ? string : null;
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text : null;
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
        UUID uuid = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        return uuid.toString().replace("-", "");
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

    private String buildStatsDocumentId(String ownerId, String normalizedEan) {
        return ownerId + "#" + normalizedEan;
    }

    private record ItemWrite(DocumentReference reference, Map<String, Object> data) {
    }

    private record StatsKey(String ownerId, String normalizedEan) {
    }

    private record StatsMetadata(String receiptId, String receiptDate, String storeName, Timestamp updatedAt) {
    }

    private static final class ItemSyncPlan {

        private static final ItemSyncPlan EMPTY = new ItemSyncPlan(List.of(), List.of(), Map.of(), Map.of());

        private final List<DocumentReference> deletions;
        private final List<ItemWrite> writes;
        private final Map<StatsKey, Long> deltas;
        private final Map<StatsKey, StatsMetadata> metadata;

        private ItemSyncPlan(List<DocumentReference> deletions, List<ItemWrite> writes,
            Map<StatsKey, Long> deltas, Map<StatsKey, StatsMetadata> metadata) {

            this.deletions = deletions != null ? List.copyOf(deletions) : List.of();
            this.writes = writes != null ? List.copyOf(writes) : List.of();
            this.deltas = deltas != null ? Map.copyOf(deltas) : Map.of();
            this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        }

        static ItemSyncPlan empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return deletions.isEmpty() && writes.isEmpty() && deltas.isEmpty();
        }

        List<DocumentReference> deletions() {
            return deletions;
        }

        List<ItemWrite> writes() {
            return writes;
        }

        Map<StatsKey, Long> deltas() {
            return deltas;
        }

        Map<StatsKey, StatsMetadata> metadata() {
            return metadata;
        }
    }
}
