package dev.pekelund.pklnd.receipts;

/**
 * Processing lifecycle states for a receipt file.
 */
public enum ReceiptProcessingStatus {

    /**
     * The receipt was uploaded and the parsing function has acknowledged the event.
     */
    RECEIVED,

    /**
     * Gemini parsing has started.
     */
    PARSING,

    /**
     * The file was skipped because it does not meet the parsing requirements.
     */
    SKIPPED,

    /**
     * The parsing completed successfully and data is stored in Firestore.
     */
    COMPLETED,

    /**
     * The parsing failed due to an unrecoverable error.
     */
    FAILED
}
