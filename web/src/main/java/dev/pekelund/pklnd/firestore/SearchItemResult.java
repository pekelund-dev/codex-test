package dev.pekelund.pklnd.firestore;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a single item found in search results.
 */
public record SearchItemResult(
    String receiptId,
    String receiptDisplayName,
    String receiptStoreName,
    String receiptDate,
    Instant receiptUpdatedAt,
    String itemName,
    String itemPrice,
    BigDecimal itemPriceValue,
    String itemQuantity,
    String itemTotal,
    BigDecimal itemTotalValue,
    String itemDiscount,
    BigDecimal itemDiscountValue
) {
}
