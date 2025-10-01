package dev.pekelund.responsiveauth.function;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Debug configuration to log Spring AI settings.
 */
@Configuration
public class DebugConfiguration {

    @Bean
    public CommandLineRunner debugChatModel(ChatModel chatModel) {
        return args -> {
            System.out.println("=== Spring AI ChatModel Configuration ===");
            System.out.println("ChatModel class: " + chatModel.getClass().getName());
            System.out.println("ChatModel: " + chatModel.toString());
            System.out.println("=== End Configuration Debug ===");
        };
    }
}
