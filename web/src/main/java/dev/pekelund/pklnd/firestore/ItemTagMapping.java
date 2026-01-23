package dev.pekelund.pklnd.firestore;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the mapping between a receipt item and an assigned tag.
 * An item can have multiple tags.
 */
public record ItemTagMapping(
    String id,
    String receiptId,
    String itemIndex,
    String itemEan,
    String tagId,
    Instant assignedAt,
    String assignedBy
) {

    public ItemTagMapping {
        Objects.requireNonNull(receiptId, "Receipt ID cannot be null");
        Objects.requireNonNull(tagId, "Tag ID cannot be null");
    }

    /**
     * Create a composite key from receipt ID, item identifier, and tag ID.
     */
    public static String createKey(String receiptId, String itemIdentifier, String tagId) {
        return receiptId + "_" + itemIdentifier + "_" + tagId;
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
        private String tagId;
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

        public Builder tagId(String tagId) {
            this.tagId = tagId;
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

        public ItemTagMapping build() {
            return new ItemTagMapping(id, receiptId, itemIndex, itemEan, tagId, assignedAt, assignedBy);
        }
    }
}
