package dev.pekelund.pklnd.receipts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "receipt.processing")
public class ReceiptProcessingProperties {

    /**
     * Optional shared secret used to validate Pub/Sub push requests. When set, the
     * controller requires the request header {@code Ce-Token} to match this value.
     */
    private String pubsubVerificationToken;

    public String getPubsubVerificationToken() {
        return pubsubVerificationToken;
    }

    public void setPubsubVerificationToken(String pubsubVerificationToken) {
        this.pubsubVerificationToken = pubsubVerificationToken;
    }
}
