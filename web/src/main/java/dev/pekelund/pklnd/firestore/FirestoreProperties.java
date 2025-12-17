package dev.pekelund.pklnd.firestore;

import dev.pekelund.pklnd.receipts.ReceiptItemConstants;
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
     * Firestore database id; defaults to the named receipts database provisioned by Terraform.
     */
    private String databaseId;

    /**
     * Optional host:port of the Firestore emulator.
     */
    private String emulatorHost;

    /**
     * Firestore collection used to persist user documents.
     */
    private String usersCollection = "users";

    /**
     * Firestore collection used to persist extracted receipt data.
     */
    private String receiptsCollection = ReceiptItemConstants.DEFAULT_RECEIPTS_COLLECTION;

    /**
     * Firestore collection used to store denormalised receipt items.
     */
    private String receiptItemsCollection = ReceiptItemConstants.DEFAULT_RECEIPT_ITEMS_COLLECTION;

    /**
     * Firestore collection used to store aggregated receipt item statistics.
     */
    private String itemStatsCollection = ReceiptItemConstants.DEFAULT_ITEM_STATS_COLLECTION;

    /**
     * Firestore collection used to store tag definitions per owner.
     */
    private String tagsCollection = "tags";

    /**
     * Firestore collection used to store mappings between EAN codes and tag ids per owner.
     */
    private String tagMappingsCollection = "tag-mappings";

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

    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public String getEmulatorHost() {
        return emulatorHost;
    }

    public void setEmulatorHost(String emulatorHost) {
        this.emulatorHost = emulatorHost;
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

    public String getReceiptItemsCollection() {
        return receiptItemsCollection;
    }

    public void setReceiptItemsCollection(String receiptItemsCollection) {
        this.receiptItemsCollection = receiptItemsCollection;
    }

    public String getItemStatsCollection() {
        return itemStatsCollection;
    }

    public void setItemStatsCollection(String itemStatsCollection) {
        this.itemStatsCollection = itemStatsCollection;
    }

    public String getTagsCollection() {
        return tagsCollection;
    }

    public void setTagsCollection(String tagsCollection) {
        this.tagsCollection = tagsCollection;
    }

    public String getTagMappingsCollection() {
        return tagMappingsCollection;
    }

    public void setTagMappingsCollection(String tagMappingsCollection) {
        this.tagMappingsCollection = tagMappingsCollection;
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
