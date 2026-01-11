package dev.pekelund.pklnd.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import dev.pekelund.pklnd.receipts.ReceiptItemConstants;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptOwnerMatcher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
public class ReceiptExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptExtractionService.class);

    private final FirestoreProperties properties;
    private final Optional<Firestore> firestore;
    private final FirestoreReadRecorder readRecorder;
    private final String receiptItemsCollection;
    private final String itemStatsCollection;

    public ReceiptExtractionService(
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
            Firestore db = firestore.get();
            Query query = db.collection(properties.getReceiptsCollection());
            String description;

            if (includeAllOwners) {
                description = "Load all receipts";
            } else {
                if (owner == null || !StringUtils.hasText(owner.id())) {
                    return List.of();
                }
                query = query.whereEqualTo("owner.id", owner.id());
                description = "Load receipts for owner " + owner.id();
            }

            QuerySnapshot snapshot = query.get().get();
            recordRead(description, snapshot != null ? snapshot.size() : 0);
            List<ParsedReceipt> receipts = new ArrayList<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                ParsedReceipt parsed = toParsedReceipt(document);
                if (parsed == null) {
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
            recordRead("Load receipt " + id, 1L);
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

    public List<ReceiptItemReference> findReceiptItemReferences(String normalizedEan, ReceiptOwner owner,
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

            List<ReceiptItemReference> references = new ArrayList<>();
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
                references.add(new ReceiptItemReference(
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

    public List<ParsedReceipt> findByIds(Collection<String> ids) {
        if (firestore.isEmpty() || ids == null || ids.isEmpty()) {
            return List.of();
        }

        Set<String> unique = ids.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty()) {
            return List.of();
        }

        try {
            Firestore db = firestore.get();
            List<String> identifiers = new ArrayList<>(unique);
            List<ParsedReceipt> receipts = new ArrayList<>();
            for (int start = 0; start < identifiers.size(); start += 10) {
                int end = Math.min(start + 10, identifiers.size());
                List<String> chunk = identifiers.subList(start, end);
                if (chunk.isEmpty()) {
                    continue;
                }
                QuerySnapshot snapshot = db.collection(properties.getReceiptsCollection())
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .get();
                recordRead("Load receipts by id chunk", snapshot != null ? snapshot.size() : 0);
                if (snapshot == null) {
                    continue;
                }
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    ParsedReceipt parsed = toParsedReceipt(document);
                    if (parsed != null) {
                        receipts.add(parsed);
                    }
                }
            }
            receipts.sort(Comparator.comparing(ParsedReceipt::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
            return List.copyOf(receipts);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading receipts by id from Firestore", ex);
            throw new ReceiptExtractionAccessException("Interrupted while loading receipts by id from Firestore.", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to load receipts by id from Firestore", ex);
            throw new ReceiptExtractionAccessException("Failed to load receipts by id from Firestore.", ex);
        }
    }

    public List<ParsedReceipt> searchByItemName(String searchQuery, ReceiptOwner owner, boolean includeAllOwners) {
        if (firestore.isEmpty() || !StringUtils.hasText(searchQuery)) {
            return List.of();
        }

        if (!includeAllOwners && owner == null) {
            return List.of();
        }

        List<ParsedReceipt> allReceipts = includeAllOwners ? listAllReceipts() : listReceiptsForOwner(owner);
        
        String normalizedQuery = searchQuery.trim().toLowerCase(Locale.ROOT);
        
        List<ParsedReceipt> matchingReceipts = new ArrayList<>();
        for (ParsedReceipt receipt : allReceipts) {
            if (receipt == null) {
                continue;
            }
            
            List<Map<String, Object>> items = receipt.displayItems();
            if (items == null || items.isEmpty()) {
                continue;
            }
            
            boolean hasMatch = false;
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                
                Object nameObj = item.get("name");
                if (nameObj == null) {
                    continue;
                }
                
                String itemName = nameObj.toString().toLowerCase(Locale.ROOT);
                if (itemName.contains(normalizedQuery)) {
                    hasMatch = true;
                    break;
                }
            }
            
            if (hasMatch) {
                matchingReceipts.add(receipt);
            }
        }
        
        matchingReceipts.sort(Comparator.comparing(ParsedReceipt::updatedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return Collections.unmodifiableList(matchingReceipts);
    }

    public List<SearchItemResult> searchItemsByName(String searchQuery, ReceiptOwner owner, boolean includeAllOwners) {
        if (firestore.isEmpty() || !StringUtils.hasText(searchQuery)) {
            return List.of();
        }

        if (!includeAllOwners && owner == null) {
            return List.of();
        }

        List<ParsedReceipt> allReceipts = includeAllOwners ? listAllReceipts() : listReceiptsForOwner(owner);
        
        String normalizedQuery = searchQuery.trim().toLowerCase(Locale.ROOT);
        
        List<SearchItemResult> matchingItems = new ArrayList<>();
        for (ParsedReceipt receipt : allReceipts) {
            if (receipt == null) {
                continue;
            }
            
            List<Map<String, Object>> items = receipt.displayItems();
            if (items == null || items.isEmpty()) {
                continue;
            }
            
            String receiptDisplayName = receipt.displayName();
            if (receiptDisplayName == null || receiptDisplayName.isBlank()) {
                receiptDisplayName = receipt.objectPath();
            }
            
            String storeName = receipt.storeName();
            if (storeName == null || storeName.isBlank()) {
                storeName = "Unknown";
            }
            
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                
                Object nameObj = item.get("name");
                if (nameObj == null) {
                    continue;
                }
                
                String itemName = nameObj.toString();
                if (itemName.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                    // Use displayUnitPrice and displayTotalPrice from displayItems()
                    String price = asString(item.get("displayUnitPrice"));
                    BigDecimal priceValue = parseBigDecimal(item.get("unitPrice"));
                    String quantity = asString(item.get("displayQuantity"));
                    String total = asString(item.get("displayTotalPrice"));
                    BigDecimal totalValue = parseBigDecimal(item.get("totalPrice"));
                    
                    // Calculate item discount
                    BigDecimal discountValue = ParsedReceipt.calculateItemDiscountTotal(item);
                    String discount = discountValue != null && discountValue.compareTo(BigDecimal.ZERO) > 0 
                        ? formatAmount(discountValue) : null;
                    
                    matchingItems.add(new SearchItemResult(
                        receipt.id(),
                        receiptDisplayName,
                        storeName,
                        receipt.receiptDate(),
                        receipt.updatedAt(),
                        itemName,
                        price,
                        priceValue,
                        quantity,
                        total,
                        totalValue,
                        discount,
                        discountValue
                    ));
                }
            }
        }
        
        // Sort by receipt date descending by default, then by item name
        matchingItems.sort(Comparator
            .comparing(SearchItemResult::receiptDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SearchItemResult::itemName, String.CASE_INSENSITIVE_ORDER));
        
        return Collections.unmodifiableList(matchingItems);
    }

    public void deleteReceiptsForOwner(ReceiptOwner owner) {
        if (owner == null || firestore.isEmpty()) {
            return;
        }

        try {
            Firestore db = firestore.get();
            // 1. Fetch all receipts for the owner
            Query query = db.collection(properties.getReceiptsCollection())
                .whereEqualTo("owner.id", owner.id());

            QuerySnapshot receiptSnapshot = query.get().get();
            recordRead("Load receipts for deletion", receiptSnapshot != null ? receiptSnapshot.size() : 0);

            if (receiptSnapshot == null || receiptSnapshot.isEmpty()) {
                return;
            }

            // 2. Collect IDs and References
            List<String> receiptIds = new ArrayList<>();
            List<DocumentReference> receiptRefs = new ArrayList<>();
            for (DocumentSnapshot doc : receiptSnapshot.getDocuments()) {
                receiptIds.add(doc.getId());
                receiptRefs.add(doc.getReference());
            }

            // 3. Process items in batches (Query by receiptId IN chunk to reduce N+1 queries)
            Map<String, Long> globalDeltas = new HashMap<>();
            
            // Firestore 'in' limit is 30
            int chunkSize = 30;
            for (int i = 0; i < receiptIds.size(); i += chunkSize) {
                int end = Math.min(receiptIds.size(), i + chunkSize);
                List<String> chunk = receiptIds.subList(i, end);
                
                deleteItemsForReceiptBatch(db, chunk, owner, globalDeltas);
            }

            // 4. Batch delete the receipt documents themselves
            batchDeleteDocuments(db, receiptRefs);

            // 5. Update stats
            if (!globalDeltas.isEmpty()) {
                applyStatsUpdates(db, globalDeltas);
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

    private void deleteItemsForReceiptBatch(Firestore db, List<String> receiptIds, ReceiptOwner owner, Map<String, Long> globalDeltas) 
            throws ExecutionException, InterruptedException {
        QuerySnapshot itemsSnapshot = db.collection(receiptItemsCollection)
            .whereIn("receiptId", receiptIds)
            .get()
            .get();
        
        recordRead("Load receipt items batch (" + receiptIds.size() + ")", itemsSnapshot != null ? itemsSnapshot.size() : 0);
        
        if (itemsSnapshot == null || itemsSnapshot.isEmpty()) {
            return;
        }

        List<DocumentReference> itemRefs = new ArrayList<>();
        
        for (QueryDocumentSnapshot document : itemsSnapshot.getDocuments()) {
            itemRefs.add(document.getReference());
            
            String ownerId = document.getString("ownerId");
            String normalizedEan = document.getString("normalizedEan");
            if (!StringUtils.hasText(normalizedEan)) {
                continue;
            }
            
            if (StringUtils.hasText(ownerId)) {
                globalDeltas.merge(buildStatsDocumentId(ownerId, normalizedEan), 1L, Long::sum);
            } else if (owner != null && StringUtils.hasText(owner.id())) {
                globalDeltas.merge(buildStatsDocumentId(owner.id(), normalizedEan), 1L, Long::sum);
            }
            globalDeltas.merge(buildStatsDocumentId(ReceiptItemConstants.GLOBAL_OWNER_ID, normalizedEan), 1L, Long::sum);
        }
        
        batchDeleteDocuments(db, itemRefs);
    }

    private void batchDeleteDocuments(Firestore db, List<DocumentReference> refs) throws ExecutionException, InterruptedException {
        int batchSize = 500;
        for (int i = 0; i < refs.size(); i += batchSize) {
            int end = Math.min(refs.size(), i + batchSize);
            List<DocumentReference> batchRefs = refs.subList(i, end);
            
            WriteBatch batch = db.batch();
            for (DocumentReference ref : batchRefs) {
                batch.delete(ref);
            }
            batch.commit().get();
        }
    }

    private void applyStatsUpdates(Firestore db, Map<String, Long> deltas)
        throws ExecutionException, InterruptedException {
        
        List<Map.Entry<String, Long>> updates = new ArrayList<>(deltas.entrySet());
        int batchSize = 400; // Safe limit below 500

        for (int i = 0; i < updates.size(); i += batchSize) {
            int end = Math.min(updates.size(), i + batchSize);
            List<Map.Entry<String, Long>> batchUpdates = updates.subList(i, end);
            
            WriteBatch batch = db.batch();
            Timestamp updateTimestamp = Timestamp.now();
            
            for (Map.Entry<String, Long> entry : batchUpdates) {
                String docId = entry.getKey();
                long decrement = entry.getValue();
                DocumentReference statsRef = db.collection(itemStatsCollection).document(docId);
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("count", FieldValue.increment(-decrement));
                updateData.put("updatedAt", updateTimestamp);
                batch.set(statsRef, updateData, SetOptions.merge());
            }
            batch.commit().get();
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
        Map<String, Object> itemHistoryRaw = toStringObjectMap(data.get("itemHistory"));
        Map<String, Long> ownerHistory = toLongMap(itemHistoryRaw.get("owner"));
        Map<String, Long> globalHistory = toLongMap(itemHistoryRaw.get("global"));
        ParsedReceipt.ReceiptItemHistory itemHistory = new ParsedReceipt.ReceiptItemHistory(ownerHistory, globalHistory);
        List<Map<String, Object>> vats = toMapList(structuredData.get("vats"));
        List<Map<String, Object>> generalDiscounts = toMapList(structuredData.get("generalDiscounts"));
        List<Map<String, Object>> errors = toMapList(structuredData.get("errors"));
        String rawText = asString(structuredData.get("rawText"));
        String stackTrace = asString(data.get("stackTrace"));

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
            itemHistory,
            vats,
            generalDiscounts,
            errors,
            rawText,
            rawResponse,
            error,
            stackTrace
        );
    }

    private Map<String, Long> toLongMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) {
                continue;
            }
            Long numeric = toLong(entry.getValue());
            if (numeric != null) {
                result.put(key.toString(), numeric);
            }
        }
        return Map.copyOf(result);
    }

    private Long toLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer intValue) {
            return intValue.longValue();
        }
        if (value instanceof Double doubleValue) {
            return doubleValue.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toDate().toInstant();
    }

    private String buildStatsDocumentId(String ownerId, String normalizedEan) {
        return ownerId + "#" + normalizedEan;
    }

    private void recordRead(String description) {
        recordRead(description, 1L);
    }

    private void recordRead(String description, long readUnits) {
        readRecorder.record(description, readUnits);
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

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString().replace(',', '.');
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public record ReceiptItemReference(
        String receiptId,
        String ownerId,
        Instant receiptUpdatedAt,
        String receiptDate,
        String receiptStoreName,
        String receiptDisplayName,
        String receiptObjectName,
        Map<String, Object> itemData
    ) {
        public ReceiptItemReference {
            itemData = itemData == null ? Map.of() : Map.copyOf(itemData);
        }
    }
}
