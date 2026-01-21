package dev.pekelund.pklnd.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for managing the relationships between receipt items and their categories/tags.
 */
@Service
public class ItemCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(ItemCategorizationService.class);
    private static final String ITEM_CATEGORIES_COLLECTION = "item_categories";
    private static final String ITEM_TAGS_COLLECTION = "item_tags";

    private final Optional<Firestore> firestore;
    private final FirestoreReadRecorder readRecorder;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;

    public ItemCategorizationService(
        ObjectProvider<Firestore> firestoreProvider,
        FirestoreReadRecorder readRecorder,
        CategoryService categoryService,
        TagService tagService,
        ObjectProvider<ReceiptExtractionService> receiptExtractionServiceProvider
    ) {
        this.firestore = Optional.ofNullable(firestoreProvider.getIfAvailable());
        this.readRecorder = readRecorder;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionServiceProvider.getIfAvailable());
    }

    public boolean isEnabled() {
        return firestore.isPresent();
    }

    /**
     * Assign a category to a receipt item.
     * Replaces any existing category assignment for this item.
     */
    public ItemCategoryMapping assignCategory(
        String receiptId,
        String itemIndex,
        String itemEan,
        String categoryId,
        String assignedBy
    ) {
        if (firestore.isEmpty()) {
            throw new IllegalStateException("Firestore is not enabled");
        }

        if (!StringUtils.hasText(receiptId)) {
            throw new IllegalArgumentException("Receipt ID cannot be empty");
        }

        if (!StringUtils.hasText(categoryId)) {
            throw new IllegalArgumentException("Category ID cannot be empty");
        }

        // Verify category exists
        Optional<Category> category = categoryService.findById(categoryId);
        if (category.isEmpty()) {
            throw new IllegalArgumentException("Category not found: " + categoryId);
        }

        String itemIdentifier = StringUtils.hasText(itemEan) ? itemEan : itemIndex;
        if (!StringUtils.hasText(itemIdentifier)) {
            throw new IllegalArgumentException("Either itemIndex or itemEan must be provided");
        }

        try {
            Firestore db = firestore.get();
            String docId = ItemCategoryMapping.createKey(receiptId, itemIdentifier);
            DocumentReference docRef = db.collection(ITEM_CATEGORIES_COLLECTION).document(docId);
            
            Instant now = Instant.now();
            Map<String, Object> data = new HashMap<>();
            data.put("receiptId", receiptId);
            data.put("itemIndex", itemIndex);
            data.put("itemEan", itemEan);
            data.put("categoryId", categoryId);
            data.put("assignedAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
            data.put("assignedBy", assignedBy);

            docRef.set(data).get();

            return ItemCategoryMapping.builder()
                .id(docId)
                .receiptId(receiptId)
                .itemIndex(itemIndex)
                .itemEan(itemEan)
                .categoryId(categoryId)
                .assignedAt(now)
                .assignedBy(assignedBy)
                .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while assigning category to item", ex);
            throw new RuntimeException("Failed to assign category", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to assign category to item", ex);
            throw new RuntimeException("Failed to assign category", ex);
        }
    }

    /**
     * Assign a category to all items with the specified EAN across all receipts.
     * This is useful for ensuring consistent categorization across all instances of the same product.
     */
    public int assignCategoryByEan(
        String itemEan,
        String categoryId,
        String assignedBy
    ) {
        if (firestore.isEmpty()) {
            throw new IllegalStateException("Firestore is not enabled");
        }

        if (!StringUtils.hasText(itemEan)) {
            throw new IllegalArgumentException("Item EAN cannot be empty");
        }

        if (!StringUtils.hasText(categoryId)) {
            throw new IllegalArgumentException("Category ID cannot be empty");
        }

        // Verify category exists
        Optional<Category> category = categoryService.findById(categoryId);
        if (category.isEmpty()) {
            throw new IllegalArgumentException("Category not found: " + categoryId);
        }

        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            log.warn("Cannot assign category by EAN: receipt extraction service not available");
            return 0;
        }

        try {
            // Get all receipts
            List<ParsedReceipt> allReceipts = receiptExtractionService.get().listAllReceipts();
            int assignedCount = 0;
            Firestore db = firestore.get();
            Instant now = Instant.now();

            // Iterate through all receipts and find items with matching EAN
            for (ParsedReceipt receipt : allReceipts) {
                List<Map<String, Object>> items = receipt.displayItems();
                for (int i = 0; i < items.size(); i++) {
                    Map<String, Object> item = items.get(i);
                    Object normalizedEanObj = item.get("normalizedEan");
                    
                    if (normalizedEanObj != null && itemEan.equals(normalizedEanObj.toString())) {
                        // Found an item with matching EAN, assign category
                        String docId = ItemCategoryMapping.createKey(receipt.id(), itemEan);
                        DocumentReference docRef = db.collection(ITEM_CATEGORIES_COLLECTION).document(docId);
                        
                        Map<String, Object> data = new HashMap<>();
                        data.put("receiptId", receipt.id());
                        data.put("itemIndex", String.valueOf(i));
                        data.put("itemEan", itemEan);
                        data.put("categoryId", categoryId);
                        data.put("assignedAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
                        data.put("assignedBy", assignedBy);

                        docRef.set(data).get();
                        assignedCount++;
                    }
                }
            }

            log.info("Assigned category {} to {} items with EAN {}", categoryId, assignedCount, itemEan);
            return assignedCount;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while assigning category by EAN", ex);
            throw new RuntimeException("Failed to assign category by EAN", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to assign category by EAN", ex);
            throw new RuntimeException("Failed to assign category by EAN", ex);
        }
    }

    /**
     * Get the category assignment for a receipt item.
     */
    public Optional<ItemCategoryMapping> getCategoryForItem(String receiptId, String itemIdentifier) {
        if (firestore.isEmpty() || !StringUtils.hasText(receiptId) || !StringUtils.hasText(itemIdentifier)) {
            return Optional.empty();
        }

        try {
            Firestore db = firestore.get();
            String docId = ItemCategoryMapping.createKey(receiptId, itemIdentifier);
            DocumentSnapshot snapshot = db.collection(ITEM_CATEGORIES_COLLECTION)
                .document(docId)
                .get()
                .get();
            recordRead("Load category for item", 1);

            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(toItemCategoryMapping(snapshot));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading category for item", ex);
            return Optional.empty();
        } catch (ExecutionException ex) {
            log.error("Failed to load category for item", ex);
            return Optional.empty();
        }
    }

    /**
     * Get all category assignments for a receipt.
     */
    public List<ItemCategoryMapping> getCategoriesForReceipt(String receiptId) {
        if (firestore.isEmpty() || !StringUtils.hasText(receiptId)) {
            return List.of();
        }

        try {
            Firestore db = firestore.get();
            QuerySnapshot snapshot = db.collection(ITEM_CATEGORIES_COLLECTION)
                .whereEqualTo("receiptId", receiptId)
                .get()
                .get();
            recordRead("Load categories for receipt", snapshot.size());

            List<ItemCategoryMapping> mappings = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ItemCategoryMapping mapping = toItemCategoryMapping(doc);
                if (mapping != null) {
                    mappings.add(mapping);
                }
            }
            return Collections.unmodifiableList(mappings);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading categories for receipt", ex);
            return List.of();
        } catch (ExecutionException ex) {
            log.error("Failed to load categories for receipt", ex);
            return List.of();
        }
    }

    /**
     * Remove category assignment from an item.
     */
    public void removeCategoryFromItem(String receiptId, String itemIdentifier) {
        if (firestore.isEmpty() || !StringUtils.hasText(receiptId) || !StringUtils.hasText(itemIdentifier)) {
            return;
        }

        try {
            Firestore db = firestore.get();
            String docId = ItemCategoryMapping.createKey(receiptId, itemIdentifier);
            db.collection(ITEM_CATEGORIES_COLLECTION).document(docId).delete().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while removing category from item", ex);
            throw new RuntimeException("Failed to remove category", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to remove category from item", ex);
            throw new RuntimeException("Failed to remove category", ex);
        }
    }

    /**
     * Assign a tag to a receipt item.
     */
    public ItemTagMapping assignTag(
        String receiptId,
        String itemIndex,
        String itemEan,
        String tagId,
        String assignedBy
    ) {
        if (firestore.isEmpty()) {
            throw new IllegalStateException("Firestore is not enabled");
        }

        if (!StringUtils.hasText(receiptId)) {
            throw new IllegalArgumentException("Receipt ID cannot be empty");
        }

        if (!StringUtils.hasText(tagId)) {
            throw new IllegalArgumentException("Tag ID cannot be empty");
        }

        // Verify tag exists
        Optional<ItemTag> tag = tagService.findById(tagId);
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("Tag not found: " + tagId);
        }

        String itemIdentifier = StringUtils.hasText(itemEan) ? itemEan : itemIndex;
        if (!StringUtils.hasText(itemIdentifier)) {
            throw new IllegalArgumentException("Either itemIndex or itemEan must be provided");
        }

        try {
            Firestore db = firestore.get();
            String docId = ItemTagMapping.createKey(receiptId, itemIdentifier, tagId);
            DocumentReference docRef = db.collection(ITEM_TAGS_COLLECTION).document(docId);
            
            Instant now = Instant.now();
            Map<String, Object> data = new HashMap<>();
            data.put("receiptId", receiptId);
            data.put("itemIndex", itemIndex);
            data.put("itemEan", itemEan);
            data.put("tagId", tagId);
            data.put("assignedAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
            data.put("assignedBy", assignedBy);

            docRef.set(data).get();

            return ItemTagMapping.builder()
                .id(docId)
                .receiptId(receiptId)
                .itemIndex(itemIndex)
                .itemEan(itemEan)
                .tagId(tagId)
                .assignedAt(now)
                .assignedBy(assignedBy)
                .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while assigning tag to item", ex);
            throw new RuntimeException("Failed to assign tag", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to assign tag to item", ex);
            throw new RuntimeException("Failed to assign tag", ex);
        }
    }

    /**
     * Assign a tag to all items with the same EAN code across all receipts.
     * Similar to assignCategoryByEan, but for tags.
     * 
     * @param itemEan The EAN code to search for
     * @param tagId The tag ID to assign
     * @param assignedBy The user assigning the tag
     * @return The number of items that were assigned the tag
     */
    public int assignTagByEan(String itemEan, String tagId, String assignedBy) {
        if (firestore.isEmpty()) {
            throw new IllegalStateException("Firestore is not enabled");
        }

        if (!StringUtils.hasText(itemEan)) {
            throw new IllegalArgumentException("Item EAN cannot be empty");
        }

        if (!StringUtils.hasText(tagId)) {
            throw new IllegalArgumentException("Tag ID cannot be empty");
        }

        // Verify tag exists
        Optional<ItemTag> tag = tagService.findById(tagId);
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("Tag not found: " + tagId);
        }

        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            log.warn("Cannot assign tag by EAN: receipt extraction service not available");
            return 0;
        }

        try {
            // Get all receipts
            List<ParsedReceipt> allReceipts = receiptExtractionService.get().listAllReceipts();
            int assignedCount = 0;
            Firestore db = firestore.get();
            Instant now = Instant.now();
            
            log.info("Scanning {} receipts for items with EAN: {}", allReceipts.size(), itemEan);
            int itemsChecked = 0;
            int itemsWithEan = 0;

            // Iterate through all receipts and find items with matching EAN
            for (ParsedReceipt receipt : allReceipts) {
                // Use displayItems() - it has eanCode at top level
                List<Map<String, Object>> items = receipt.displayItems();
                
                for (int i = 0; i < items.size(); i++) {
                    Map<String, Object> item = items.get(i);
                    itemsChecked++;
                    
                    // Check for eanCode field (the actual field name in displayItems)
                    Object eanCodeObj = item.get("eanCode");
                    
                    if (eanCodeObj != null && itemEan.equals(eanCodeObj.toString())) {
                        // Found an item with matching EAN, assign tag
                        itemsWithEan++;
                        
                        // Use itemIndex as the identifier in the document ID
                        String itemIdentifier = String.valueOf(i);
                        String docId = ItemTagMapping.createKey(receipt.id(), itemIdentifier, tagId);
                        DocumentReference docRef = db.collection(ITEM_TAGS_COLLECTION).document(docId);
                        
                        Map<String, Object> data = new HashMap<>();
                        data.put("receiptId", receipt.id());
                        data.put("itemIndex", itemIdentifier);
                        data.put("itemEan", itemEan);
                        data.put("tagId", tagId);
                        data.put("assignedAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
                        data.put("assignedBy", assignedBy);

                        docRef.set(data).get();
                        assignedCount++;
                        log.debug("Assigned tag {} to item {} in receipt {} (EAN: {})", 
                            tagId, itemIdentifier, receipt.id(), itemEan);
                    }
                }
            }

            log.info("Scanned {} items total, {} had EAN codes. Assigned tag {} to {} items with EAN {}", 
                itemsChecked, itemsWithEan, tagId, assignedCount, itemEan);
            return assignedCount;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while assigning tag by EAN", ex);
            throw new RuntimeException("Failed to assign tag by EAN", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to assign tag by EAN", ex);
            throw new RuntimeException("Failed to assign tag by EAN", ex);
        }
    }

    /**
     * Get all tags assigned to a receipt item.
     */
    public List<ItemTagMapping> getTagsForItem(String receiptId, String itemIdentifier) {
        if (firestore.isEmpty() || !StringUtils.hasText(receiptId) || !StringUtils.hasText(itemIdentifier)) {
            return List.of();
        }

        try {
            Firestore db = firestore.get();
            QuerySnapshot snapshot = db.collection(ITEM_TAGS_COLLECTION)
                .whereEqualTo("receiptId", receiptId)
                .get()
                .get();
            recordRead("Load tags for item", snapshot.size());

            List<ItemTagMapping> mappings = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ItemTagMapping mapping = toItemTagMapping(doc);
                if (mapping != null) {
                    String docItemIdentifier = StringUtils.hasText(mapping.itemEan()) 
                        ? mapping.itemEan() 
                        : mapping.itemIndex();
                    if (itemIdentifier.equals(docItemIdentifier)) {
                        mappings.add(mapping);
                    }
                }
            }
            return Collections.unmodifiableList(mappings);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading tags for item", ex);
            return List.of();
        } catch (ExecutionException ex) {
            log.error("Failed to load tags for item", ex);
            return List.of();
        }
    }

    /**
     * Get all tag assignments for a receipt.
     */
    public List<ItemTagMapping> getTagsForReceipt(String receiptId) {
        if (firestore.isEmpty() || !StringUtils.hasText(receiptId)) {
            return List.of();
        }

        try {
            Firestore db = firestore.get();
            QuerySnapshot snapshot = db.collection(ITEM_TAGS_COLLECTION)
                .whereEqualTo("receiptId", receiptId)
                .get()
                .get();
            recordRead("Load tags for receipt", snapshot.size());

            List<ItemTagMapping> mappings = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ItemTagMapping mapping = toItemTagMapping(doc);
                if (mapping != null) {
                    mappings.add(mapping);
                }
            }
            return Collections.unmodifiableList(mappings);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading tags for receipt", ex);
            return List.of();
        } catch (ExecutionException ex) {
            log.error("Failed to load tags for receipt", ex);
            return List.of();
        }
    }

    /**
     * Remove a tag from a receipt item.
     */
    public void removeTagFromItem(String receiptId, String itemIdentifier, String tagId) {
        if (firestore.isEmpty() || !StringUtils.hasText(receiptId) 
            || !StringUtils.hasText(itemIdentifier) || !StringUtils.hasText(tagId)) {
            return;
        }

        try {
            Firestore db = firestore.get();
            String docId = ItemTagMapping.createKey(receiptId, itemIdentifier, tagId);
            db.collection(ITEM_TAGS_COLLECTION).document(docId).delete().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while removing tag from item", ex);
            throw new RuntimeException("Failed to remove tag", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to remove tag from item", ex);
            throw new RuntimeException("Failed to remove tag", ex);
        }
    }

    private ItemCategoryMapping toItemCategoryMapping(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        Map<String, Object> data = doc.getData();
        if (data == null) {
            return null;
        }

        return ItemCategoryMapping.builder()
            .id(doc.getId())
            .receiptId((String) data.get("receiptId"))
            .itemIndex((String) data.get("itemIndex"))
            .itemEan((String) data.get("itemEan"))
            .categoryId((String) data.get("categoryId"))
            .assignedAt(toInstant(data.get("assignedAt")))
            .assignedBy((String) data.get("assignedBy"))
            .build();
    }

    private ItemTagMapping toItemTagMapping(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        Map<String, Object> data = doc.getData();
        if (data == null) {
            return null;
        }

        return ItemTagMapping.builder()
            .id(doc.getId())
            .receiptId((String) data.get("receiptId"))
            .itemIndex((String) data.get("itemIndex"))
            .itemEan((String) data.get("itemEan"))
            .tagId((String) data.get("tagId"))
            .assignedAt(toInstant(data.get("assignedAt")))
            .assignedBy((String) data.get("assignedBy"))
            .build();
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        }
        return null;
    }

    /**
     * Get all items that have a specific tag assigned across all receipts.
     * Returns a list of tuples containing the receipt and item index.
     */
    public List<TaggedItemInfo> getItemsByTag(String tagId) {
        if (firestore.isEmpty() || !StringUtils.hasText(tagId)) {
            return List.of();
        }

        try {
            Firestore db = firestore.get();
            QuerySnapshot snapshot = db.collection(ITEM_TAGS_COLLECTION)
                .whereEqualTo("tagId", tagId)
                .get()
                .get();
            recordRead("Load items by tag", snapshot.size());

            List<TaggedItemInfo> items = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ItemTagMapping mapping = toItemTagMapping(doc);
                if (mapping != null) {
                    items.add(new TaggedItemInfo(
                        mapping.receiptId(),
                        mapping.itemIndex(),
                        mapping.itemEan(),
                        mapping.assignedAt()
                    ));
                }
            }
            return Collections.unmodifiableList(items);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading items by tag", ex);
            return List.of();
        } catch (ExecutionException ex) {
            log.error("Failed to load items by tag", ex);
            return List.of();
        }
    }

    /**
     * Simple record to hold information about an item that has a tag assigned.
     */
    public record TaggedItemInfo(
        String receiptId,
        String itemIndex,
        String itemEan,
        Instant assignedAt
    ) {}

    private void recordRead(String description, long count) {
        if (readRecorder != null) {
            readRecorder.record(description, count);
        }
    }
}
