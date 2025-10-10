package dev.pekelund.pklnd.firestore;

public class UserRoleUpdateException extends RuntimeException {

    public UserRoleUpdateException(String message) {
        super(message);
    }

    public UserRoleUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
