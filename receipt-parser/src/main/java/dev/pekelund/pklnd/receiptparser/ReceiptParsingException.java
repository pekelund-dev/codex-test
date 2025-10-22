package dev.pekelund.pklnd.receiptparser;

/**
 * Signals a failure while parsing a receipt.
 */
public class ReceiptParsingException extends RuntimeException {

    public ReceiptParsingException(String message) {
        super(message);
    }

    public ReceiptParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
