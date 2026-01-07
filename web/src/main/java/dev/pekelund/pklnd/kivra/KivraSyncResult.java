package dev.pekelund.pklnd.kivra;

/**
 * Result of a Kivra synchronization operation.
 */
public record KivraSyncResult(
    boolean success,
    boolean authenticationPending,
    String qrCodeData,
    int uploadedCount,
    int failureCount,
    String message
) {
    public static KivraSyncResult success(int uploadedCount, int failureCount, String message) {
        return new KivraSyncResult(true, false, null, uploadedCount, failureCount, message);
    }

    public static KivraSyncResult failure(String message) {
        return new KivraSyncResult(false, false, null, 0, 0, message);
    }

    public static KivraSyncResult authenticationPending(String qrCodeData, String message) {
        return new KivraSyncResult(false, true, qrCodeData, 0, 0, message);
    }
}
