package dev.pekelund.pklnd.receipts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(ReceiptProcessingProperties.class)
public class ReceiptProcessingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "receipt.processing", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("'${receipt.processing.base-url:}' != ''")
    public ReceiptProcessingClient receiptProcessingClient(RestTemplateBuilder restTemplateBuilder,
        ReceiptProcessingProperties properties) {
        RestTemplate restTemplate = restTemplateBuilder
            .setConnectTimeout(properties.getConnectTimeout())
            .setReadTimeout(properties.getReadTimeout())
            .build();
        return new ReceiptProcessingClient(restTemplate, properties);
    }
}
