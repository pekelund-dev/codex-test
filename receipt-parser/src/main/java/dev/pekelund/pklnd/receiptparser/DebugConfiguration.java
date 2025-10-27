package dev.pekelund.pklnd.receiptparser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Debug configuration to log Spring AI settings.
 */
@Configuration
public class DebugConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugConfiguration.class);

    @Bean
    public CommandLineRunner debugChatModel(ChatModel chatModel) {
        return args -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("=== Spring AI ChatModel Configuration ===");
                LOGGER.debug("ChatModel class: {}", chatModel.getClass().getName());
                LOGGER.debug("ChatModel: {}", chatModel);
                LOGGER.debug("=== End Configuration Debug ===");
            }
        };
    }
}
