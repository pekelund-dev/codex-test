package dev.pekelund.pklnd.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class FirestoreMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirestoreMigrationRunner.class);
    private static final String MIGRATIONS_COLLECTION = "schema_migrations";

    private final FirestoreProperties properties;
    private final Optional<Firestore> firestore;
    private final List<FirestoreMigration> migrations;

    public FirestoreMigrationRunner(
        FirestoreProperties properties,
        ObjectProvider<Firestore> firestoreProvider,
        List<FirestoreMigration> migrations
    ) {
        this.properties = properties;
        this.firestore = Optional.ofNullable(firestoreProvider.getIfAvailable());
        this.migrations = migrations;
    }

    @EventListener
    public void runMigrations(ApplicationReadyEvent event) {
        if (!properties.isEnabled() || firestore.isEmpty()) {
            return;
        }
        if (CollectionUtils.isEmpty(migrations)) {
            log.info("No Firestore migrations registered.");
            return;
        }

        List<FirestoreMigration> ordered = migrations.stream()
            .sorted(Comparator.comparingInt(FirestoreMigration::version))
            .toList();

        Firestore db = firestore.get();
        for (FirestoreMigration migration : ordered) {
            try {
                if (isApplied(db, migration)) {
                    continue;
                }
                log.info("Applying Firestore migration {} - {}", migration.version(), migration.description());
                migration.apply(db);
                markApplied(db, migration);
            } catch (Exception ex) {
                log.error("Failed Firestore migration {}", migration.version(), ex);
                throw new IllegalStateException("Firestore migration failed", ex);
            }
        }
    }

    private boolean isApplied(Firestore db, FirestoreMigration migration) {
        DocumentSnapshot snapshot = getFuture(
            db.collection(MIGRATIONS_COLLECTION)
                .document(String.valueOf(migration.version()))
                .get(),
            migration.version(),
            "Migration check"
        );
        return snapshot.exists();
    }

    private void markApplied(Firestore db, FirestoreMigration migration) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("version", migration.version());
        payload.put("description", migration.description());
        payload.put("appliedAt", Timestamp.now());
        getFuture(
            db.collection(MIGRATIONS_COLLECTION)
                .document(String.valueOf(migration.version()))
                .set(payload),
            migration.version(),
            "Mark migration applied"
        );
    }

    private <T> T getFuture(Future<T> future, int version, String action) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(action + " interrupted for version " + version, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException(
                "Failed to " + action.toLowerCase() + " for version " + version,
                cause
            );
        }
    }
}
