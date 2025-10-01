package dev.pekelund.responsiveauth.function;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Emits diagnostic logging when the Cloud Function boots so we can verify the
 * deployed artifact and configuration.
 */
@Component
public class FunctionDiagnostics implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionDiagnostics.class);

    private final Environment environment;
    private final VertexAiGeminiChatOptions chatOptions;
    private final ChatModel chatModel;

    public FunctionDiagnostics(Environment environment, VertexAiGeminiChatOptions chatOptions, ChatModel chatModel) {
        this.environment = environment;
        this.chatOptions = chatOptions;
        this.chatModel = chatModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("ResponsiveAuth receipt function diagnostics starting");
        LOGGER.info("Active Spring profiles: {}", Arrays.toString(environment.getActiveProfiles()));
        LOGGER.info("Resolved Vertex AI configuration - project: {}, location: {}, model: {}",
            environment.getProperty("spring.ai.vertex.ai.gemini.project-id", "(unset)"),
            environment.getProperty("spring.ai.vertex.ai.gemini.location", "(unset)"),
            environment.getProperty("spring.ai.vertex.ai.gemini.model", "(unset)"));
        LOGGER.info("Environment override VERTEX_AI_GEMINI_MODEL={} (System.getenv)",
            System.getenv().getOrDefault("VERTEX_AI_GEMINI_MODEL", "(unset)"));
        LOGGER.info("Resolved chat options model: {} (instance id {})", chatOptions.getModel(),
            System.identityHashCode(chatOptions));
        LOGGER.info("ChatModel implementation: {} - default options: {}", chatModel.getClass().getName(),
            chatModel.getDefaultOptions());
    }
}
