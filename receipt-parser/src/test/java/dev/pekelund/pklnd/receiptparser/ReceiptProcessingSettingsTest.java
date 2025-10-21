package dev.pekelund.pklnd.receiptparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ReceiptProcessingSettingsTest {

    private static final Supplier<String> NO_PROJECT = () -> null;

    @Test
    void fromEnvironmentUsesExplicitProjectId() {
        Map<String, String> env = new HashMap<>();
        env.put("RECEIPT_FIRESTORE_PROJECT_ID", "explicit-project");
        env.put("RECEIPT_FIRESTORE_COLLECTION", "custom-collection");

        ReceiptProcessingSettings settings = ReceiptProcessingSettings.fromEnvironment(env, NO_PROJECT);

        assertThat(settings.projectId()).isEqualTo("explicit-project");
        assertThat(settings.receiptsCollection()).isEqualTo("custom-collection");
    }

    @Test
    void fromEnvironmentThrowsWhenDefaultLocalProjectDetectedOnCloudRun() {
        Map<String, String> env = new HashMap<>();
        env.put("RECEIPT_FIRESTORE_PROJECT_ID", "pklnd-local");
        env.put("K_SERVICE", "pklnd-receipts");

        assertThatThrownBy(() -> ReceiptProcessingSettings.fromEnvironment(env, () -> "codex-test-473008"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("local project 'pklnd-local'");
    }

    @Test
    void fromEnvironmentThrowsWhenCustomLocalProjectDetectedOnCloudRun() {
        Map<String, String> env = new HashMap<>();
        env.put("LOCAL_PROJECT_ID", "custom-local");
        env.put("RECEIPT_FIRESTORE_PROJECT_ID", "custom-local");
        env.put("K_SERVICE", "pklnd-receipts");

        assertThatThrownBy(() -> ReceiptProcessingSettings.fromEnvironment(env, () -> "codex-test-473008"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("local project 'custom-local'");
    }

    @Test
    void fromEnvironmentUsesMetadataProjectWhenAvailable() {
        Map<String, String> env = new HashMap<>();

        ReceiptProcessingSettings settings = ReceiptProcessingSettings.fromEnvironment(env, () -> "codex-test-473008");

        assertThat(settings.projectId()).isEqualTo("codex-test-473008");
    }

    @Test
    void fromEnvironmentThrowsWhenProjectIdMissing() {
        Map<String, String> env = new HashMap<>();

        assertThatThrownBy(() -> ReceiptProcessingSettings.fromEnvironment(env, NO_PROJECT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Firestore project id must be configured");
    }
}
