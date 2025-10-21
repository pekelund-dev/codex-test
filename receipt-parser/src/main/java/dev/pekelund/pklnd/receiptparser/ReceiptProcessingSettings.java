package dev.pekelund.pklnd.receiptparser;

import com.google.cloud.ServiceOptions;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Configuration values resolved for the receipt parsing Cloud Run service.
 * Vertex AI settings are handled by Spring AI auto-configuration.
 */
public record ReceiptProcessingSettings(
    String projectId,
    String receiptsCollection
) {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessingSettings.class);
    private static final String DEFAULT_COLLECTION = "receiptExtractions";
    private static final String LOCAL_PROJECT_ID = "responsive-auth-local";

    public static ReceiptProcessingSettings fromEnvironment() {
        return fromEnvironment(System.getenv(), ServiceOptions::getDefaultProjectId);
    }

    static ReceiptProcessingSettings fromEnvironment(Map<String, String> env,
        Supplier<String> defaultProjectSupplier) {

        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(defaultProjectSupplier, "defaultProjectSupplier");

        String collection = env.getOrDefault("RECEIPT_FIRESTORE_COLLECTION", DEFAULT_COLLECTION);
        String projectId = firstNonEmpty(
            env.get("RECEIPT_FIRESTORE_PROJECT_ID"),
            env.get("GOOGLE_CLOUD_PROJECT"),
            env.get("GCLOUD_PROJECT"),
            env.get("GCP_PROJECT"),
            env.get("PROJECT_ID"),
            defaultProjectSupplier.get());

        if (LOCAL_PROJECT_ID.equals(projectId) && isRunningOnCloudRun(env)) {
            String fallbackProject = defaultProjectSupplier.get();
            if (StringUtils.hasText(fallbackProject) && !LOCAL_PROJECT_ID.equals(fallbackProject)) {
                LOGGER.warn("Resolved Firestore project id '{}' from environment, but the service is running on Cloud Run. "
                        + "Falling back to metadata project id '{}'.", projectId, fallbackProject);
                projectId = fallbackProject;
            }
        }

        if (!StringUtils.hasText(projectId)) {
            throw new IllegalStateException("Firestore project id must be configured via RECEIPT_FIRESTORE_PROJECT_ID "
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
