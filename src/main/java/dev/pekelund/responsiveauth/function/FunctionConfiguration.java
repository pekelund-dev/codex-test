package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.vertexai.VertexAI;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.retry.support.RetryTemplate;

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
        String projectId = environment.getProperty("spring.ai.vertex.ai.gemini.project-id", "(unset)");
        String location = environment.getProperty("spring.ai.vertex.ai.gemini.location", "(unset)");
        LOGGER.info("Configured Vertex AI Gemini chat settings - project: {}, location: {}, model: {}", projectId, location,
            modelName);
        return VertexAiGeminiChatOptions.builder()
            .model(modelName)
            .build();
    }

    @Bean
    @Primary
    public VertexAiGeminiChatModel vertexAiGeminiChatModel(VertexAI vertexAI,
        VertexAiGeminiChatOptions receiptGeminiChatOptions,
        ObjectProvider<ToolCallingManager> toolCallingManager,
        ObjectProvider<RetryTemplate> retryTemplate,
        ObjectProvider<ObservationRegistry> observationRegistry,
        ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {

        ToolCallingManager resolvedToolCallingManager = toolCallingManager
            .getIfAvailable(() -> DefaultToolCallingManager.builder().build());
        RetryTemplate resolvedRetryTemplate = retryTemplate
            .getIfAvailable(() -> RetryTemplate.builder().build());
        ObservationRegistry resolvedObservationRegistry = observationRegistry
            .getIfAvailable(() -> ObservationRegistry.NOOP);
        ToolExecutionEligibilityPredicate resolvedEligibilityPredicate = toolExecutionEligibilityPredicate
            .getIfAvailable(DefaultToolExecutionEligibilityPredicate::new);

        VertexAiGeminiChatModel chatModel = new VertexAiGeminiChatModel(vertexAI, receiptGeminiChatOptions,
            resolvedToolCallingManager, resolvedRetryTemplate, resolvedObservationRegistry, resolvedEligibilityPredicate);
        LOGGER.info("Vertex AI Gemini ChatModel default options: {}", chatModel.getDefaultOptions());
        return chatModel;
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
