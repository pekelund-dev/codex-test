package dev.pekelund.pklnd.firestore;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a category or subcategory for receipt items.
 * Categories can have a hierarchical structure where a category can have a parent.
 */
public record Category(
    String id,
    String name,
    String parentId,
    Instant createdAt,
    Instant updatedAt,
    boolean predefined
) {

    public Category {
        Objects.requireNonNull(name, "Category name cannot be null");
    }

    /**
     * Check if this is a top-level category (no parent).
     */
    public boolean isTopLevel() {
        return parentId == null || parentId.isBlank();
    }

    /**
     * Check if this is a predefined system category.
     */
    public boolean isPredefined() {
        return predefined;
    }

    /**
     * Create a new category builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String parentId;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean predefined;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder predefined(boolean predefined) {
            this.predefined = predefined;
            return this;
        }

        public Category build() {
            return new Category(id, name, parentId, createdAt, updatedAt, predefined);
        }
    }
}
