package dev.pekelund.pklnd.firestore;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.firestore.v1.FirestoreAdminClient;
import com.google.firestore.admin.v1.ExportDocumentsRequest;
import com.google.firestore.admin.v1.ExportDocumentsResponse;
import com.google.firestore.admin.v1.ImportDocumentsRequest;
import com.google.firestore.admin.v1.ImportDocumentsMetadata;
import com.google.protobuf.Empty;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FirestoreBackupService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreBackupService.class);
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        .withZone(ZoneOffset.UTC);

    private final FirestoreProperties properties;

    public FirestoreBackupService(FirestoreProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled()
            && StringUtils.hasText(properties.getProjectId())
            && StringUtils.hasText(properties.getDatabaseId())
            && StringUtils.hasText(properties.getBackupBucket())
            && !StringUtils.hasText(properties.getEmulatorHost());
    }

    public BackupOperation startExport(String label) {
        ensureEnabled();
        String outputUri = buildBackupUri(label);
        String databaseName = databaseName();

        try (FirestoreAdminClient client = FirestoreAdminClient.create()) {
            ExportDocumentsRequest request = ExportDocumentsRequest.newBuilder()
                .setName(databaseName)
                .setOutputUriPrefix(outputUri)
                .build();
            OperationFuture<ExportDocumentsResponse, ?> operation = client.exportDocumentsAsync(request);
            log.info("Started Firestore export {} for {}", operation.getName(), outputUri);
            return new BackupOperation(operation.getName(), outputUri);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to start Firestore export", ex);
        }
    }

    public BackupOperation startImport(String inputUri) {
        ensureEnabled();
        if (!StringUtils.hasText(inputUri) || !inputUri.startsWith("gs://")) {
            throw new IllegalArgumentException("Import path must be a gs:// URI");
        }

        String databaseName = databaseName();

        try (FirestoreAdminClient client = FirestoreAdminClient.create()) {
            ImportDocumentsRequest request = ImportDocumentsRequest.newBuilder()
                .setName(databaseName)
                .setInputUriPrefix(inputUri.trim())
                .build();
            OperationFuture<Empty, ImportDocumentsMetadata> operation = client.importDocumentsAsync(request);
            log.info("Started Firestore import {} from {}", operation.getName(), inputUri);
            return new BackupOperation(operation.getName(), inputUri.trim());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to start Firestore import", ex);
        }
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Firestore backup operations are not configured");
        }
    }

    private String buildBackupUri(String label) {
        String prefix = StringUtils.hasText(properties.getBackupPrefix())
            ? properties.getBackupPrefix().trim()
            : "exports";
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        String suffix = StringUtils.hasText(label) ? "-" + sanitizeLabel(label) : "";
        return String.format("gs://%s/%s/%s%s", properties.getBackupBucket().trim(), prefix, timestamp, suffix);
    }

    private String sanitizeLabel(String label) {
        String trimmed = label.trim().toLowerCase();
        String sanitized = trimmed.replaceAll("[^a-z0-9_-]", "-");
        return sanitized.replaceAll("-{2,}", "-");
    }

    private String databaseName() {
        return String.format("projects/%s/databases/%s", properties.getProjectId(), properties.getDatabaseId());
    }

    public record BackupOperation(String operationName, String uri) {}
}
