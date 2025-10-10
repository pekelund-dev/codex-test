package dev.pekelund.pklnd.firestore;

public class ReceiptExtractionAccessException extends RuntimeException {

    public ReceiptExtractionAccessException(String message) {
        super(message);
    }

    public ReceiptExtractionAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
