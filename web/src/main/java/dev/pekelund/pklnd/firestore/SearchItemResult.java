package dev.pekelund.pklnd.firestore;

import java.time.Instant;

/**
 * Represents a single item found in search results.
 */
public record SearchItemResult(
    String receiptId,
    String receiptDisplayName,
    String receiptDate,
    Instant receiptUpdatedAt,
    String itemName,
    String itemPrice,
    String itemQuantity,
    String itemTotal
) {
}
