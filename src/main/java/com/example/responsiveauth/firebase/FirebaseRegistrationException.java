package com.example.responsiveauth.firebase;

public class FirebaseRegistrationException extends RuntimeException {

    public FirebaseRegistrationException(String message) {
        super(message);
    }

    public FirebaseRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
