package dev.pekelund.responsiveauth.function;

import java.util.Map;

/**
 * Configuration values resolved for the receipt parsing Cloud Function.
 * Vertex AI settings are handled by Spring AI auto-configuration.
 */
public record ReceiptProcessingSettings(
    String receiptsCollection
) {

    private static final String DEFAULT_COLLECTION = "receiptExtractions";

    public static ReceiptProcessingSettings fromEnvironment() {
        Map<String, String> env = System.getenv();
        String collection = env.getOrDefault("RECEIPT_FIRESTORE_COLLECTION", DEFAULT_COLLECTION);
        return new ReceiptProcessingSettings(collection);
    }
}
