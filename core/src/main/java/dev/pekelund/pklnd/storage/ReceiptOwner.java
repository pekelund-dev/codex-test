package dev.pekelund.pklnd.storage;

import java.util.HashMap;
import java.util.Map;

public record ReceiptOwner(String id, String displayName, String email) {

    public static final String METADATA_OWNER_ID = "receipt.owner.id";
    public static final String METADATA_OWNER_DISPLAY_NAME = "receipt.owner.displayName";
    public static final String METADATA_OWNER_EMAIL = "receipt.owner.email";

    public ReceiptOwner {
        id = normalize(id);
        displayName = normalize(displayName);
        email = normalize(email);
    }

    public boolean hasValues() {
        return id != null || displayName != null || email != null;
    }

    public Map<String, String> toMetadata() {
        Map<String, String> metadata = new HashMap<>();
        if (id != null) {
            metadata.put(METADATA_OWNER_ID, id);
        }
        if (displayName != null) {
            metadata.put(METADATA_OWNER_DISPLAY_NAME, displayName);
        }
        if (email != null) {
            metadata.put(METADATA_OWNER_EMAIL, email);
        }
        return metadata;
    }

    public Map<String, String> toAttributes() {
        Map<String, String> attributes = new HashMap<>();
        if (id != null) {
            attributes.put("id", id);
        }
        if (displayName != null) {
            attributes.put("displayName", displayName);
        }
        if (email != null) {
            attributes.put("email", email);
        }
        return attributes;
    }

    public static ReceiptOwner fromMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        ReceiptOwner owner = new ReceiptOwner(
            metadata.get(METADATA_OWNER_ID),
            metadata.get(METADATA_OWNER_DISPLAY_NAME),
            metadata.get(METADATA_OWNER_EMAIL));
        return owner.hasValues() ? owner : null;
    }

    public static ReceiptOwner fromAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        ReceiptOwner owner = new ReceiptOwner(
            attributes.get("id"),
            attributes.get("displayName"),
            attributes.get("email"));
        return owner.hasValues() ? owner : null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

