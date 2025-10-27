package dev.pekelund.pklnd.receiptparser.local;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    public ReceiptDataExtractor receiptDataExtractor(LegacyPdfReceiptExtractor legacyPdfReceiptExtractor) {
        return legacyPdfReceiptExtractor;
    }

    @Bean(name = "codexReceiptDataExtractor")
    public ReceiptDataExtractor codexReceiptDataExtractor(ObjectMapper objectMapper) {
        return new CodexOnlyReceiptDataExtractor(objectMapper);
    }

    @Bean
    @Primary
    public ChatModel chatModel() {
        return new NoopChatModel();
    }
}
