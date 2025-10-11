package dev.pekelund.pklnd.storage;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

    public String displayName() {
        if (!hasText(name)) {
            return "—";
        }

        String stripped = stripGeneratedPrefix(name);
        String decoded = decodeSafely(stripped);
        String candidate = hasText(decoded) ? decoded : name;
        return truncate(candidate, 48);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stripGeneratedPrefix(String value) {
        int firstUnderscore = value.indexOf('_');
        if (firstUnderscore < 0) {
            return value;
        }
        int secondUnderscore = value.indexOf('_', firstUnderscore + 1);
        if (secondUnderscore < 0) {
            return value.substring(firstUnderscore + 1);
        }
        if (secondUnderscore + 1 >= value.length()) {
            return value;
        }
        return value.substring(secondUnderscore + 1);
    }

    private String decodeSafely(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private String truncate(String value, int maxLength) {
        if (!hasText(value) || value.length() <= maxLength) {
            return value;
        }
        int safeLength = Math.max(1, maxLength - 1);
        return value.substring(0, safeLength) + "…";
    }
}

