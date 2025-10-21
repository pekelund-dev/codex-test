package dev.pekelund.pklnd.receiptparser;

import java.util.Map;

/**
 * Configuration values resolved for the receipt parsing Cloud Run service.
 * Vertex AI settings are handled by Spring AI auto-configuration.
 */
public record ReceiptProcessingSettings(
    String projectId,
    String receiptsCollection
) {

    private static final String DEFAULT_COLLECTION = "receiptExtractions";

    public static ReceiptProcessingSettings fromEnvironment() {
        Map<String, String> env = System.getenv();
        String collection = env.getOrDefault("RECEIPT_FIRESTORE_COLLECTION", DEFAULT_COLLECTION);
        String projectId = firstNonEmpty(
            env.get("RECEIPT_FIRESTORE_PROJECT_ID"),
            env.get("GOOGLE_CLOUD_PROJECT"),
            env.get("GCLOUD_PROJECT"),
            env.get("GCP_PROJECT"));
        return new ReceiptProcessingSettings(projectId, collection);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
