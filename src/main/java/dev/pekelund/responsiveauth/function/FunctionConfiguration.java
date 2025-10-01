package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class FunctionConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionConfiguration.class);

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean
    public VertexAiGeminiChatOptions receiptGeminiChatOptions(Environment environment) {
        String modelName = environment.getProperty(
            "spring.ai.vertex.ai.gemini.model",
            environment.getProperty("spring.ai.vertex.ai.gemini.chat.options.model", "gemini-2.0-flash"));
        LOGGER.info("Configured Vertex AI Gemini chat model: {}", modelName);
        return VertexAiGeminiChatOptions.builder()
            .model(modelName)
            .build();
    }

    @Bean
    public GeminiReceiptExtractor geminiReceiptExtractor(ChatModel chatModel, ObjectMapper objectMapper,
        VertexAiGeminiChatOptions receiptGeminiChatOptions) {
        return new GeminiReceiptExtractor(chatModel, objectMapper, receiptGeminiChatOptions);
    }

    @Bean
    public Firestore firestore() {
        return FirestoreOptions.getDefaultInstance().getService();
    }

    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }

    @Bean
    public ReceiptProcessingSettings receiptProcessingSettings() {
        return ReceiptProcessingSettings.fromEnvironment();
    }

    @Bean
    public ReceiptExtractionRepository receiptExtractionRepository(Firestore firestore,
        ReceiptProcessingSettings receiptProcessingSettings) {
        return new ReceiptExtractionRepository(firestore, receiptProcessingSettings.receiptsCollection());
    }

    @Bean
    public ReceiptParsingHandler receiptParsingHandler(Storage storage, ReceiptExtractionRepository receiptExtractionRepository,
        GeminiReceiptExtractor geminiReceiptExtractor) {
        return new ReceiptParsingHandler(storage, receiptExtractionRepository, geminiReceiptExtractor);
    }
}
