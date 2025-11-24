package dev.pekelund.pklnd.receipts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ReceiptProcessingProperties.class)
public class ReceiptProcessingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "receipt.processing", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("'${receipt.processing.base-url:}' != ''")
    public ReceiptProcessingClient receiptProcessingClient(RestClient.Builder restClientBuilder,
        ReceiptProcessingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());

        RestClient restClient = restClientBuilder
            .requestFactory(requestFactory)
            .build();
        return new ReceiptProcessingClient(restClient, properties);
    }
}
