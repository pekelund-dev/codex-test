package dev.pekelund.pklnd.receiptparser.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.pklnd.receiptparser.AIReceiptExtractor;
import dev.pekelund.pklnd.receiptparser.HybridReceiptExtractor;
import dev.pekelund.pklnd.receiptparser.ReceiptDataExtractor;
import dev.pekelund.pklnd.receiptparser.legacy.CodexOnlyReceiptDataExtractor;
import dev.pekelund.pklnd.receiptparser.legacy.LegacyPdfReceiptExtractor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local-receipt-test")
public class LocalReceiptTestConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean
    @Primary
    public ChatModel chatModel() {
        return new NoopChatModel();
    }

    @Bean
    public AIReceiptExtractor aiReceiptExtractor(ChatModel chatModel, ObjectMapper objectMapper) {
        return new AIReceiptExtractor(chatModel, objectMapper, null);
    }

    @Bean
    public HybridReceiptExtractor hybridReceiptExtractor(LegacyPdfReceiptExtractor legacyPdfReceiptExtractor,
        AIReceiptExtractor aiReceiptExtractor, ObjectMapper objectMapper) {
        return new HybridReceiptExtractor(legacyPdfReceiptExtractor, aiReceiptExtractor, objectMapper);
    }

    @Bean
    @Primary
    public ReceiptDataExtractor receiptDataExtractor(LegacyPdfReceiptExtractor legacyPdfReceiptExtractor) {
        return legacyPdfReceiptExtractor;
    }

    @Bean(name = "codexReceiptDataExtractor")
    public ReceiptDataExtractor codexReceiptDataExtractor(ObjectMapper objectMapper) {
        return new CodexOnlyReceiptDataExtractor(objectMapper);
    }
}
