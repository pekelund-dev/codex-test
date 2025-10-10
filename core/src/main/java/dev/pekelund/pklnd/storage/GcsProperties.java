package dev.pekelund.pklnd.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcs")
public class GcsProperties {

    /**
     * Flag indicating whether Google Cloud Storage integration is enabled.
     */
    private boolean enabled;

    /**
     * Optional path or resource string that resolves to the service account credentials file.
     * When omitted, application default credentials will be used.
     */
    private String credentials;

    /**
     * Optional Google Cloud project identifier used when building the storage client.
     */
    private String projectId;

    /**
     * Name of the Google Cloud Storage bucket that stores receipt uploads.
     */
    private String bucket;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}

