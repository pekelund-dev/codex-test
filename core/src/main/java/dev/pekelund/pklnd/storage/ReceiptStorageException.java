package dev.pekelund.pklnd.storage;

public class ReceiptStorageException extends RuntimeException {

    public ReceiptStorageException(String message) {
        super(message);
    }

    public ReceiptStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

