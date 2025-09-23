package com.example.responsiveauth.firebase;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firebase")
public class FirebaseProperties {

    /**
     * Flag indicating whether Firebase integration is enabled.
     */
    private boolean enabled;

    /**
     * Path or resource descriptor to the Firebase service account credentials file.
     */
    private String credentials;

    /**
     * Web API key used for Firebase Identity Toolkit requests.
     */
    private String apiKey;

    /**
     * Firestore collection used to persist user profile documents.
     */
    private String usersCollection = "users";

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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getUsersCollection() {
        return usersCollection;
    }

    public void setUsersCollection(String usersCollection) {
        this.usersCollection = usersCollection;
    }
}
