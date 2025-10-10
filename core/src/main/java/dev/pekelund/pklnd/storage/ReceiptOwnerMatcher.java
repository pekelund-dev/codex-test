package dev.pekelund.pklnd.storage;

import org.springframework.util.StringUtils;

/**
 * Utility methods for comparing {@link ReceiptOwner} instances.
 */
public final class ReceiptOwnerMatcher {

    private ReceiptOwnerMatcher() {
    }

    /**
     * Determines whether the provided file owner matches the current authenticated owner.
     *
     * @param fileOwner    the owner associated with the stored resource
     * @param currentOwner the owner resolved from the current authentication context
     * @return {@code true} if the owners represent the same person, otherwise {@code false}
     */
    public static boolean belongsToCurrentOwner(ReceiptOwner fileOwner, ReceiptOwner currentOwner) {
        if (fileOwner == null || currentOwner == null) {
            return false;
        }

        if (matches(fileOwner.id(), currentOwner.id())) {
            return true;
        }

        if (matchesIgnoreCase(fileOwner.email(), currentOwner.email())) {
            return true;
        }

        return matchesIgnoreCase(fileOwner.displayName(), currentOwner.displayName());
    }

    private static boolean matches(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equals(right);
    }

    private static boolean matchesIgnoreCase(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right);
    }
}
