package dev.pekelund.pklnd.receipts;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "receipt.processing")
public class ReceiptProcessingProperties {

    /**
     * Flag indicating whether the web application should notify the receipt processor after uploads.
     */
    private boolean enabled = true;

    /**
     * Base URL of the receipt processor Cloud Run service.
     */
    private String baseUrl;

    /**
     * Path that accepts Cloud Storage style events on the receipt processor.
     */
    private String eventPath = "/events/storage";

    /**
     * Whether to attach an ID token to each request for service-to-service authentication.
     */
    private boolean useIdToken = true;

    /**
     * Optional audience to include when minting the ID token. Defaults to the base URL.
     */
    private String audience;

    /**
     * HTTP connect timeout used when calling the receipt processor.
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * HTTP read timeout used when calling the receipt processor.
     */
    private Duration readTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEventPath() {
        return eventPath;
    }

    public void setEventPath(String eventPath) {
        this.eventPath = eventPath;
    }

    public boolean isUseIdToken() {
        return useIdToken;
    }

    public void setUseIdToken(boolean useIdToken) {
        this.useIdToken = useIdToken;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isConfigured() {
        return enabled && StringUtils.hasText(baseUrl);
    }
}
