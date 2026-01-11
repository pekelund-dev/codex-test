package dev.pekelund.pklnd.firestore;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the mapping between a receipt item and its assigned category/subcategory.
 * An item can be mapped to one category at a time.
 */
public record ItemCategoryMapping(
    String id,
    String receiptId,
    String itemIndex,
    String itemEan,
    String categoryId,
    Instant assignedAt,
    String assignedBy
) {

    public ItemCategoryMapping {
        Objects.requireNonNull(receiptId, "Receipt ID cannot be null");
        Objects.requireNonNull(categoryId, "Category ID cannot be null");
    }

    /**
     * Create a composite key from receipt ID and item identifier.
     */
    public static String createKey(String receiptId, String itemIdentifier) {
        return receiptId + "_" + itemIdentifier;
    }

    /**
     * Create a new mapping builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String receiptId;
        private String itemIndex;
        private String itemEan;
        private String categoryId;
        private Instant assignedAt;
        private String assignedBy;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder receiptId(String receiptId) {
            this.receiptId = receiptId;
            return this;
        }

        public Builder itemIndex(String itemIndex) {
            this.itemIndex = itemIndex;
            return this;
        }

        public Builder itemEan(String itemEan) {
            this.itemEan = itemEan;
            return this;
        }

        public Builder categoryId(String categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public Builder assignedAt(Instant assignedAt) {
            this.assignedAt = assignedAt;
            return this;
        }

        public Builder assignedBy(String assignedBy) {
            this.assignedBy = assignedBy;
            return this;
        }

        public ItemCategoryMapping build() {
            return new ItemCategoryMapping(id, receiptId, itemIndex, itemEan, categoryId, assignedAt, assignedBy);
        }
    }
}
