package dev.pekelund.pklnd.firestore;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a tag that can be applied to receipt items.
 * Tags provide additional classification beyond categories (e.g., "Fryst", "Konserv").
 */
public record ItemTag(
    String id,
    String name,
    Instant createdAt,
    boolean predefined
) {

    public ItemTag {
        Objects.requireNonNull(name, "Tag name cannot be null");
    }

    /**
     * Check if this is a predefined system tag.
     */
    public boolean isPredefined() {
        return predefined;
    }

    /**
     * Create a new tag builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private Instant createdAt;
        private boolean predefined;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder predefined(boolean predefined) {
            this.predefined = predefined;
            return this;
        }

        public ItemTag build() {
            return new ItemTag(id, name, createdAt, predefined);
        }
    }
}
