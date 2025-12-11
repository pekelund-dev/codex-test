package dev.pekelund.pklnd.storage;

/**
 * Represents a failure to upload a receipt file.
 */
public record UploadFailure(
    String filename,
    String errorMessage,
    boolean isDuplicate
) {
    
    public UploadFailure {
        filename = filename != null ? filename : "unknown";
        errorMessage = errorMessage != null ? errorMessage : "Unknown error";
    }
    
    public static UploadFailure duplicate(String filename, String existingFile) {
        String message = "Kvittot '%s' har redan laddats upp tidigare.".formatted(filename);
        return new UploadFailure(filename, message, true);
    }
    
    public static UploadFailure error(String filename, String errorMessage) {
        return new UploadFailure(filename, errorMessage, false);
    }
}
