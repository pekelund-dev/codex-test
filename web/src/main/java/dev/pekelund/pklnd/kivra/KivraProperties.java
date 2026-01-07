package dev.pekelund.pklnd.kivra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Kivra integration.
 */
@Component
@ConfigurationProperties(prefix = "kivra")
public class KivraProperties {

    /**
     * Enable Kivra integration
     */
    private boolean enabled = false;

    /**
     * Swedish personal identity number (personnummer) for Kivra authentication
     */
    private String personalNumber;

    /**
     * Base URL for Kivra API
     */
    private String apiBaseUrl = "https://app.kivra.com";

    /**
     * Maximum number of documents to fetch per sync
     */
    private int maxDocuments = 100;

    /**
     * Whether to fetch only PDF documents
     */
    private boolean pdfOnly = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPersonalNumber() {
        return personalNumber;
    }

    public void setPersonalNumber(String personalNumber) {
        this.personalNumber = personalNumber;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public int getMaxDocuments() {
        return maxDocuments;
    }

    public void setMaxDocuments(int maxDocuments) {
        this.maxDocuments = maxDocuments;
    }

    public boolean isPdfOnly() {
        return pdfOnly;
    }

    public void setPdfOnly(boolean pdfOnly) {
        this.pdfOnly = pdfOnly;
    }
}
