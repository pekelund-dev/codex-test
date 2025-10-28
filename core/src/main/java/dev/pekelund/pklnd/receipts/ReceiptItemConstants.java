package dev.pekelund.pklnd.receipts;

/**
 * Shared constants describing the Firestore layout used for parsed receipts and
 * derived indexes.
 */
public final class ReceiptItemConstants {

    /**
     * Default Firestore collection containing receipt extraction documents.
     */
    public static final String DEFAULT_RECEIPTS_COLLECTION = "receiptExtractions";

    /**
     * Default Firestore collection containing denormalised receipt line items.
     */
    public static final String DEFAULT_RECEIPT_ITEMS_COLLECTION = "receiptItems";

    /**
     * Default Firestore collection that stores aggregated item statistics.
     */
    public static final String DEFAULT_ITEM_STATS_COLLECTION = "receiptItemStats";

    /**
     * Synthetic owner identifier used for item statistics that span all
     * accounts. The same value must be used by both the receipt processor and
     * the web frontend.
     */
    public static final String GLOBAL_OWNER_ID = "__all__";

    private ReceiptItemConstants() {
    }
}
