package dev.pekelund.pklnd.kivra;

/**
 * Result of Kivra authentication attempt.
 */
public record KivraAuthenticationResult(
    boolean success,
    String qrCodeData,
    String message
) {
    public static KivraAuthenticationResult success(String message) {
        return new KivraAuthenticationResult(true, null, message);
    }

    public static KivraAuthenticationResult pending(String qrCodeData) {
        return new KivraAuthenticationResult(false, qrCodeData, "Väntar på BankID-autentisering");
    }

    public static KivraAuthenticationResult failure(String message) {
        return new KivraAuthenticationResult(false, null, message);
    }
}
