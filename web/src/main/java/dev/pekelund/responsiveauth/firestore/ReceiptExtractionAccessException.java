package dev.pekelund.responsiveauth.firestore;

public class ReceiptExtractionAccessException extends RuntimeException {

    public ReceiptExtractionAccessException(String message) {
        super(message);
    }

    public ReceiptExtractionAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
