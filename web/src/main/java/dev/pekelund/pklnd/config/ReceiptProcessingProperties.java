package dev.pekelund.pklnd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipt-processor")
public record ReceiptProcessingProperties(
    String baseUrl,
    String audience
) {
}
