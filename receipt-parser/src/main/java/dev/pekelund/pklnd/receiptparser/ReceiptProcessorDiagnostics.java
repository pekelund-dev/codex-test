package dev.pekelund.pklnd.receiptparser;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Emits diagnostic logging when the Cloud Run service boots so we can verify the
 * deployed artifact and configuration.
 */
@Component
public class ReceiptProcessorDiagnostics implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessorDiagnostics.class);

    private final Environment environment;
    private final ObjectProvider<VertexAiGeminiChatOptions> chatOptionsProvider;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public ReceiptProcessorDiagnostics(Environment environment,
        ObjectProvider<VertexAiGeminiChatOptions> chatOptionsProvider,
        ObjectProvider<ChatModel> chatModelProvider) {
        this.environment = environment;
        this.chatOptionsProvider = chatOptionsProvider;
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("ResponsiveAuth receipt processor diagnostics starting");
        LOGGER.info("Active Spring profiles: {}", Arrays.toString(environment.getActiveProfiles()));
        LOGGER.info("Resolved Vertex AI configuration - project: {}, location: {}, model: {}",
            environment.getProperty("spring.ai.vertex.ai.gemini.project-id", "(unset)"),
            environment.getProperty("spring.ai.vertex.ai.gemini.location", "(unset)"),
            environment.getProperty("spring.ai.vertex.ai.gemini.model", "(unset)"));
        LOGGER.info("Environment override VERTEX_AI_GEMINI_MODEL={} (System.getenv)",
            System.getenv().getOrDefault("VERTEX_AI_GEMINI_MODEL", "(unset)"));
        VertexAiGeminiChatOptions chatOptions = chatOptionsProvider.getIfAvailable();
        if (chatOptions != null) {
            LOGGER.info("Resolved chat options model: {} (instance id {})", chatOptions.getModel(),
                System.identityHashCode(chatOptions));
        } else {
            LOGGER.info("VertexAiGeminiChatOptions bean not available; skipping chat options diagnostics");
        }

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null) {
            LOGGER.info("ChatModel implementation: {} - default options: {}", chatModel.getClass().getName(),
                chatModel.getDefaultOptions());
        } else {
            LOGGER.info("ChatModel bean not available; skipping chat model diagnostics");
        }
    }
}
