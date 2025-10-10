package dev.pekelund.pklnd.function;

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
