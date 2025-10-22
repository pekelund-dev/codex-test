package dev.pekelund.pklnd.storage;

import java.util.Objects;

/**
 * Reference to a receipt object stored in Google Cloud Storage.
 */
public record StoredReceiptReference(String bucket, String objectName, ReceiptOwner owner) {

    public StoredReceiptReference {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(objectName, "objectName");
    }

    public StoredReceiptReference(String bucket, String objectName) {
        this(bucket, objectName, null);
    }
}
