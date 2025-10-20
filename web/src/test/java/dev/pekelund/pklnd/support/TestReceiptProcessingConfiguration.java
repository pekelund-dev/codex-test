package dev.pekelund.pklnd.support;

import com.google.cloud.storage.Storage;
import com.google.cloud.vertexai.VertexAI;
import dev.pekelund.pklnd.receipts.AIReceiptExtractor;
import dev.pekelund.pklnd.receipts.ReceiptDataExtractor;
import dev.pekelund.pklnd.receipts.ReceiptExtractionRepository;
import dev.pekelund.pklnd.receipts.ReceiptProcessingService;
import org.mockito.Mockito;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestReceiptProcessingConfiguration {

    @Bean
    @Primary
    public Storage storage() {
        return Mockito.mock(Storage.class);
    }

    @Bean
    @Primary
    public ReceiptExtractionRepository receiptExtractionRepository() {
        return Mockito.mock(ReceiptExtractionRepository.class);
    }

    @Bean
    @Primary
    public ReceiptProcessingService receiptProcessingService() {
        return Mockito.mock(ReceiptProcessingService.class);
    }

    @Bean
    @Primary
    public ReceiptDataExtractor receiptDataExtractor() {
        return Mockito.mock(ReceiptDataExtractor.class);
    }

    @Bean
    @Primary
    public AIReceiptExtractor aiReceiptExtractor() {
        return Mockito.mock(AIReceiptExtractor.class);
    }

    @Bean
    @Primary
    public VertexAI vertexAI() {
        return Mockito.mock(VertexAI.class);
    }

    @Bean
    @Primary
    public VertexAiGeminiChatModel vertexAiGeminiChatModel() {
        return Mockito.mock(VertexAiGeminiChatModel.class);
    }
}
