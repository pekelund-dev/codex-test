package dev.pekelund.responsiveauth.function;

import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Configuration values resolved for the receipt parsing Cloud Function.
 */
public record ReceiptProcessingSettings(
    String vertexProjectId,
    String vertexLocation,
    String vertexModel,
    String receiptsCollection
) {

    private static final String DEFAULT_MODEL = "gemini-1.5-pro";
    private static final String DEFAULT_LOCATION = "us-central1";
    private static final String DEFAULT_COLLECTION = "receiptExtractions";

    public static ReceiptProcessingSettings fromEnvironment() {
        Map<String, String> env = System.getenv();

        String projectId = firstNonEmpty(env.get("VERTEX_AI_PROJECT_ID"), env.get("GOOGLE_CLOUD_PROJECT"));
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalStateException("Vertex AI project id must be supplied via VERTEX_AI_PROJECT_ID or GOOGLE_CLOUD_PROJECT");
        }

        String location = env.getOrDefault("VERTEX_AI_LOCATION", DEFAULT_LOCATION);
        String model = env.getOrDefault("VERTEX_AI_GEMINI_MODEL", DEFAULT_MODEL);
        String collection = env.getOrDefault("RECEIPT_FIRESTORE_COLLECTION", DEFAULT_COLLECTION);

        return new ReceiptProcessingSettings(projectId, location, model, collection);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
