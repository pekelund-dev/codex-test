package dev.pekelund.pklnd.kivra;

/**
 * Exception thrown when Kivra client operations fail.
 */
public class KivraClientException extends Exception {

    public KivraClientException(String message) {
        super(message);
    }

    public KivraClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
