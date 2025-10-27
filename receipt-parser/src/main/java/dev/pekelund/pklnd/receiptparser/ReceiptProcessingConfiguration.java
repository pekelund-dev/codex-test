package dev.pekelund.pklnd.receiptparser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import dev.pekelund.pklnd.receiptparser.legacy.LegacyPdfReceiptExtractor;
import dev.pekelund.pklnd.receiptparser.googleai.GoogleAiGeminiChatModel;
import dev.pekelund.pklnd.receiptparser.googleai.GoogleAiGeminiChatOptions;

/**
 * Service configuration for the receipt processing Cloud Run workload.
 */
@Configuration
@Profile("!local-receipt-test")
public class ReceiptProcessingConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessingConfiguration.class);

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean
    public GoogleAiGeminiChatOptions receiptGeminiChatOptions(Environment environment) {
        String modelName = environment.getProperty("google.ai.gemini.model",
            environment.getProperty("google.ai.gemini.chat.options.model", "gemini-2.0-flash"));
        Double temperature = environment.getProperty("google.ai.gemini.temperature", Double.class);
        Double topP = environment.getProperty("google.ai.gemini.top-p", Double.class);
        Integer topK = environment.getProperty("google.ai.gemini.top-k", Integer.class);
        Integer maxOutputTokens = environment.getProperty("google.ai.gemini.max-output-tokens", Integer.class);
        LOGGER.info("Configured Google AI Gemini chat settings - model: {}, temperature: {}, topP: {}, topK: {}, maxOutputTokens: {}",
            modelName, temperature, topP, topK, maxOutputTokens);
        return GoogleAiGeminiChatOptions.builder()
            .model(modelName)
            .temperature(temperature)
            .topP(topP)
            .topK(topK)
            .maxOutputTokens(maxOutputTokens)
            .build();
    }

    @Bean
    @Primary
    public GoogleAiGeminiChatModel googleAiGeminiChatModel(Environment environment,
        GoogleAiGeminiChatOptions receiptGeminiChatOptions,
        ObjectProvider<ObservationRegistry> observationRegistry) {

        String apiKey = environment.getProperty("AI_STUDIO_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Google AI Studio API key must be configured (AI_STUDIO_API_KEY)");
        }

        ObservationRegistry resolvedObservationRegistry = observationRegistry
            .getIfAvailable(() -> ObservationRegistry.NOOP);

        String baseUrl = environment.getProperty("google.ai.gemini.base-url",
            GoogleAiGeminiChatModel.DEFAULT_BASE_URL);
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();

        GoogleAiGeminiChatModel chatModel = new GoogleAiGeminiChatModel(restClient, apiKey, receiptGeminiChatOptions,
            resolvedObservationRegistry);
        LOGGER.info("Google AI Gemini ChatModel default options: {}", chatModel.getDefaultOptions());
        LOGGER.info("Google AI Gemini ChatModel instance id {}", System.identityHashCode(chatModel));
        return chatModel;
    }

    @Bean
    public AIReceiptExtractor aiReceiptExtractor(ChatModel chatModel, ObjectMapper objectMapper,
        GoogleAiGeminiChatOptions receiptGeminiChatOptions) {
        return new AIReceiptExtractor(chatModel, objectMapper, receiptGeminiChatOptions);
    }

    @Bean
    @Primary
    public ReceiptDataExtractor receiptDataExtractor(LegacyPdfReceiptExtractor legacyPdfReceiptExtractor,
        AIReceiptExtractor aiReceiptExtractor, ObjectMapper objectMapper) {
        return new HybridReceiptExtractor(legacyPdfReceiptExtractor, aiReceiptExtractor, objectMapper);
    }

    @Bean
    public Firestore firestore(ReceiptProcessingSettings receiptProcessingSettings) {
        FirestoreOptions.Builder optionsBuilder = FirestoreOptions.getDefaultInstance().toBuilder();
        if (StringUtils.hasText(receiptProcessingSettings.projectId())) {
            optionsBuilder.setProjectId(receiptProcessingSettings.projectId());
        }
        Firestore firestore = optionsBuilder.build().getService();
        LOGGER.info("Initialized Firestore client for project '{}' (target collection '{}')",
            firestore.getOptions().getProjectId(), receiptProcessingSettings.receiptsCollection());
        return firestore;
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
        ReceiptDataExtractor receiptDataExtractor) {
        return new ReceiptParsingHandler(storage, receiptExtractionRepository, receiptDataExtractor);
    }
}
