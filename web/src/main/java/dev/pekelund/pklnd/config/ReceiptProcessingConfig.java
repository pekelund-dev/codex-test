package dev.pekelund.pklnd.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(ReceiptProcessingProperties.class)
public class ReceiptProcessingConfig {

    private static final Logger log = LoggerFactory.getLogger(ReceiptProcessingConfig.class);

    private final ReceiptProcessingProperties properties;

    public ReceiptProcessingConfig(ReceiptProcessingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void warnIfProcessorConfigurationMissing() {
        if (!StringUtils.hasText(properties.baseUrl())) {
            log.warn(
                "Receipt processor integration is disabled. Set RECEIPT_PROCESSOR_BASE_URL and RECEIPT_PROCESSOR_AUDIENCE to enable downstream parsing."
            );
            return;
        }

        if (!StringUtils.hasText(properties.audience())) {
            log.warn(
                "Receipt processor audience is not configured. Set RECEIPT_PROCESSOR_AUDIENCE so authenticated calls target the correct audience."
            );
        }
    }
}
