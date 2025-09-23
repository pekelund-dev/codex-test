package com.example.responsiveauth.firestore;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firestore")
public class FirestoreProperties {

    /**
     * Flag indicating whether Firestore integration is enabled.
     */
    private boolean enabled;

    /**
     * Path or resource descriptor to the service account credentials file.
     */
    private String credentials;

    /**
     * Optional Google Cloud project identifier.
     */
    private String projectId;

    /**
     * Firestore collection used to persist user documents.
     */
    private String usersCollection = "users";

    /**
     * Default role granted to newly registered users.
     */
    private String defaultRole = "ROLE_USER";

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

    public String getUsersCollection() {
        return usersCollection;
    }

    public void setUsersCollection(String usersCollection) {
        this.usersCollection = usersCollection;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }
}
