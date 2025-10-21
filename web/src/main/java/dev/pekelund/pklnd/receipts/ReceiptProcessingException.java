package dev.pekelund.pklnd.receipts;

public class ReceiptProcessingException extends RuntimeException {

    public ReceiptProcessingException(String message) {
        super(message);
    }

    public ReceiptProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
