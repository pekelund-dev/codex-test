package dev.pekelund.pklnd.kivra;

/**
 * Exception thrown when Kivra authentication fails.
 */
public class KivraAuthenticationException extends KivraClientException {

    public KivraAuthenticationException(String message) {
        super(message);
    }

    public KivraAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
