package dev.pekelund.pklnd.receiptparser;

import com.google.cloud.ServiceOptions;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.util.StringUtils;

/**
 * Configuration values resolved for the receipt parsing Cloud Run service.
 * Gemini API settings are handled by {@link dev.pekelund.pklnd.receiptparser.googleai.GoogleAiGeminiClient}.
 */
public record ReceiptProcessingSettings(
    String projectId,
    String receiptsCollection
) {

    private static final String DEFAULT_COLLECTION = "receiptExtractions";
    private static final String DEFAULT_LOCAL_PROJECT_ID = "pklnd-local";

    public static ReceiptProcessingSettings fromEnvironment() {
        return fromEnvironment(System.getenv(), ServiceOptions::getDefaultProjectId);
    }

    static ReceiptProcessingSettings fromEnvironment(Map<String, String> env,
        Supplier<String> defaultProjectSupplier) {

        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(defaultProjectSupplier, "defaultProjectSupplier");

        String collection = env.getOrDefault("RECEIPT_FIRESTORE_COLLECTION", DEFAULT_COLLECTION);
        String projectId = firstNonEmpty(
            env.get("PROJECT_ID"),
            env.get("FIRESTORE_PROJECT_ID"),
            env.get("GOOGLE_CLOUD_PROJECT"),
            env.get("GCLOUD_PROJECT"),
            env.get("GCP_PROJECT"),
            defaultProjectSupplier.get());

        String localProjectId = env.getOrDefault("LOCAL_PROJECT_ID", DEFAULT_LOCAL_PROJECT_ID);

        if (StringUtils.hasText(localProjectId) && localProjectId.equals(projectId) && isRunningOnCloudRun(env)) {
            throw new IllegalStateException(String.format("Firestore project id resolved to local project '%s' while running on Cloud Run. Update the deployment environment to use the production project id.", projectId));
        }

        if (!StringUtils.hasText(projectId)) {
            throw new IllegalStateException("Firestore project id must be configured via PROJECT_ID "
                + "or available from the Cloud environment.");
        }

        return new ReceiptProcessingSettings(projectId, collection);
    }

    private static boolean isRunningOnCloudRun(Map<String, String> env) {
        return StringUtils.hasText(env.get("K_SERVICE"));
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
