package dev.pekelund.pklnd.receiptparser;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.pekelund.pklnd.receiptparser.googleai.GoogleAiGeminiChatOptions;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import dev.pekelund.pklnd.receiptparser.googleai.GeminiClient;

/**
 * Emits diagnostic logging when the Cloud Run service boots so we can verify the
 * deployed artifact and configuration.
 */
@Component
public class ReceiptProcessorDiagnostics implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessorDiagnostics.class);

    private final Environment environment;
    private final ObjectProvider<GoogleAiGeminiChatOptions> chatOptionsProvider;
    private final ObjectProvider<GeminiClient> geminiClientProvider;

    public ReceiptProcessorDiagnostics(Environment environment,
        ObjectProvider<GoogleAiGeminiChatOptions> chatOptionsProvider,
        ObjectProvider<GeminiClient> geminiClientProvider) {
        this.environment = environment;
        this.chatOptionsProvider = chatOptionsProvider;
        this.geminiClientProvider = geminiClientProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("ResponsiveAuth receipt processor diagnostics starting");
        LOGGER.info("Active Spring profiles: {}", Arrays.toString(environment.getActiveProfiles()));
        LOGGER.info("Resolved Google AI Gemini configuration - model: {}",
            environment.getProperty("google.ai.gemini.model", "(unset)"));
        LOGGER.info("Environment override GOOGLE_AI_GEMINI_MODEL={} (System.getenv)",
            System.getenv().getOrDefault("GOOGLE_AI_GEMINI_MODEL", "(unset)"));
        GoogleAiGeminiChatOptions chatOptions = chatOptionsProvider.getIfAvailable();
        if (chatOptions != null) {
            LOGGER.info("Resolved chat options model: {} (instance id {})", chatOptions.getModel(),
                System.identityHashCode(chatOptions));
        } else {
            LOGGER.info("GoogleAiGeminiChatOptions bean not available; skipping chat options diagnostics");
        }

        GeminiClient geminiClient = geminiClientProvider.getIfAvailable();
        if (geminiClient != null) {
            LOGGER.info("Gemini client implementation: {} - default options: {}", geminiClient.getClass().getName(),
                geminiClient.getDefaultOptions());
        } else {
            LOGGER.info("Gemini client bean not available; skipping client diagnostics");
        }
    }
}
