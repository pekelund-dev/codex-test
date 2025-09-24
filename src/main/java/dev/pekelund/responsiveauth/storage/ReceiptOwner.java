package dev.pekelund.responsiveauth.storage;

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

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

