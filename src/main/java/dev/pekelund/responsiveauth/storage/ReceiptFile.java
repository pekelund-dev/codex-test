package dev.pekelund.responsiveauth.storage;

import java.time.Instant;

public record ReceiptFile(String name, long size, Instant updated, String contentType, ReceiptOwner owner) {

    public String formattedSize() {
        if (size <= 0) {
            return "0 B";
        }

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double readableSize = size;
        int unitIndex = 0;
        while (readableSize >= 1024 && unitIndex < units.length - 1) {
            readableSize /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", readableSize, units[unitIndex]);
    }

    public String ownerDisplayName() {
        if (owner == null) {
            return "—";
        }

        if (hasText(owner.displayName())) {
            return owner.displayName();
        }

        if (hasText(owner.email())) {
            return owner.email();
        }

        if (hasText(owner.id())) {
            return owner.id();
        }

        return "—";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

