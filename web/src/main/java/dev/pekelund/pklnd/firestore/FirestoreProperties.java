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
     * Default role granted to newly registered users.
     */
    private String defaultRole = "ROLE_USER";

    /**
     * Firestore collection used to store cached tag summaries.
     */
    private String tagSummariesCollection = "tagSummaries";

    /**
     * Firestore collection used to track tag summary change timestamps.
     */
    private String tagSummaryMetaCollection = "tagSummaryMeta";

    /**
     * Cloud Storage bucket used for Firestore exports and imports.
     */
    private String backupBucket;

    /**
     * Prefix inside the backup bucket for Firestore export folders.
     */
    private String backupPrefix = "exports";

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

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public String getTagSummariesCollection() {
        return tagSummariesCollection;
    }

    public void setTagSummariesCollection(String tagSummariesCollection) {
        this.tagSummariesCollection = tagSummariesCollection;
    }

    public String getTagSummaryMetaCollection() {
        return tagSummaryMetaCollection;
    }

    public void setTagSummaryMetaCollection(String tagSummaryMetaCollection) {
        this.tagSummaryMetaCollection = tagSummaryMetaCollection;
    }

    public String getBackupBucket() {
        return backupBucket;
    }

    public void setBackupBucket(String backupBucket) {
        this.backupBucket = backupBucket;
    }

    public String getBackupPrefix() {
        return backupPrefix;
    }

    public void setBackupPrefix(String backupPrefix) {
        if (backupPrefix != null) {
            this.backupPrefix = backupPrefix;
        }
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
