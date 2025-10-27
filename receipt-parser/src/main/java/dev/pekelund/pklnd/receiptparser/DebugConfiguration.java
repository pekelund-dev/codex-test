package dev.pekelund.pklnd.receiptparser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.pekelund.pklnd.receiptparser.googleai.GeminiClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Debug configuration to log Gemini client settings.
 */
@Configuration
public class DebugConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugConfiguration.class);

    @Bean
    public CommandLineRunner debugGeminiClient(ObjectProvider<GeminiClient> geminiClientProvider) {
        return args -> {
            GeminiClient geminiClient = geminiClientProvider.getIfAvailable();
            if (geminiClient != null && LOGGER.isDebugEnabled()) {
                LOGGER.debug("=== Gemini Client Configuration ===");
                LOGGER.debug("Client class: {}", geminiClient.getClass().getName());
                LOGGER.debug("Default options: {}", geminiClient.getDefaultOptions());
                LOGGER.debug("=== End Configuration Debug ===");
            }
        };
    }
}
