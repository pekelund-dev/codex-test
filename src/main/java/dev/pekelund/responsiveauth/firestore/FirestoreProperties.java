package dev.pekelund.responsiveauth.firestore;

import java.util.ArrayList;
import java.util.List;
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
     * Firestore collection used to persist extracted receipt data.
     */
    private String receiptsCollection = "receiptExtractions";

    /**
     * Default role granted to newly registered users.
     */
    private String defaultRole = "ROLE_USER";

    /**
     * Optional list of in-memory fallback users created when Firestore is disabled.
     */
    private List<FallbackUser> fallbackUsers = new ArrayList<>();

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

    public String getReceiptsCollection() {
        return receiptsCollection;
    }

    public void setReceiptsCollection(String receiptsCollection) {
        this.receiptsCollection = receiptsCollection;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public List<FallbackUser> getFallbackUsers() {
        return fallbackUsers;
    }

    public void setFallbackUsers(List<FallbackUser> fallbackUsers) {
        this.fallbackUsers = fallbackUsers != null ? fallbackUsers : new ArrayList<>();
    }

    public static class FallbackUser {

        /**
         * Username for the fallback account.
         */
        private String username;

        /**
         * Plain text password that will be encoded on startup.
         */
        private String password;

        /**
         * Roles granted to the fallback account.
         */
        private List<String> roles = new ArrayList<>();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
        }
    }
}
