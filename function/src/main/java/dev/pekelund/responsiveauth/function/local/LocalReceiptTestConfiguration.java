package dev.pekelund.responsiveauth.function.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.responsiveauth.function.ReceiptDataExtractor;
import dev.pekelund.responsiveauth.function.legacy.LegacyPdfReceiptExtractor;
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
}
