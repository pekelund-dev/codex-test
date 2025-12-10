package dev.pekelund.pklnd.storage;

/**
 * Exception thrown when attempting to upload a receipt that has already been uploaded.
 */
public class DuplicateReceiptException extends ReceiptStorageException {

    private final String existingObjectName;
    private final String contentHash;

    public DuplicateReceiptException(String message, String existingObjectName, String contentHash) {
        super(message);
        this.existingObjectName = existingObjectName;
        this.contentHash = contentHash;
    }

    public String getExistingObjectName() {
        return existingObjectName;
    }

    public String getContentHash() {
        return contentHash;
    }
}
