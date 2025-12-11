package dev.pekelund.pklnd.storage;

import java.util.List;

/**
 * Result of uploading multiple receipt files, containing both successful uploads and any failures.
 */
public record UploadResult(
    List<StoredReceiptReference> uploadedReceipts,
    List<UploadFailure> failures
) {
    
    public UploadResult {
        uploadedReceipts = uploadedReceipts != null ? List.copyOf(uploadedReceipts) : List.of();
        failures = failures != null ? List.copyOf(failures) : List.of();
    }
    
    public boolean hasSuccesses() {
        return !uploadedReceipts.isEmpty();
    }
    
    public boolean hasFailures() {
        return !failures.isEmpty();
    }
    
    public int successCount() {
        return uploadedReceipts.size();
    }
    
    public int failureCount() {
        return failures.size();
    }
}
