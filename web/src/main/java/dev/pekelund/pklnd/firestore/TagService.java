package dev.pekelund.pklnd.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for managing item tags.
 */
@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);
    private static final String TAGS_COLLECTION = "tags";

    private final Optional<Firestore> firestore;
    private final FirestoreReadRecorder readRecorder;

    public TagService(
        ObjectProvider<Firestore> firestoreProvider,
        FirestoreReadRecorder readRecorder
    ) {
        this.firestore = Optional.ofNullable(firestoreProvider.getIfAvailable());
        this.readRecorder = readRecorder;
    }

    public boolean isEnabled() {
        return firestore.isPresent();
    }

    /**
     * List all tags ordered by name.
     */
    public List<ItemTag> listTags() {
        if (firestore.isEmpty()) {
            return List.of();
        }

        try {
            Firestore db = firestore.get();
            QuerySnapshot snapshot = db.collection(TAGS_COLLECTION)
                .orderBy("name")
                .get()
                .get();
            recordRead("Load all tags", snapshot.size());

            List<ItemTag> tags = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ItemTag tag = toTag(doc);
                if (tag != null) {
                    tags.add(tag);
                }
            }
            return Collections.unmodifiableList(tags);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading tags from Firestore", ex);
            return List.of();
        } catch (ExecutionException ex) {
            log.error("Failed to load tags from Firestore", ex);
            return List.of();
        }
    }

    /**
     * Find a tag by ID.
     */
    public Optional<ItemTag> findById(String id) {
        if (firestore.isEmpty() || !StringUtils.hasText(id)) {
            return Optional.empty();
        }

        try {
            DocumentReference ref = firestore.get()
                .collection(TAGS_COLLECTION)
                .document(id);
            DocumentSnapshot snapshot = ref.get().get();
            recordRead("Load tag " + id, 1);

            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(toTag(snapshot));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading tag {} from Firestore", id, ex);
            return Optional.empty();
        } catch (ExecutionException ex) {
            log.error("Failed to load tag {} from Firestore", id, ex);
            return Optional.empty();
        }
    }

    /**
     * Find a tag by name (case-insensitive).
     */
    public Optional<ItemTag> findByName(String name) {
        if (firestore.isEmpty() || !StringUtils.hasText(name)) {
            return Optional.empty();
        }

        try {
            Firestore db = firestore.get();
            QuerySnapshot snapshot = db.collection(TAGS_COLLECTION)
                .whereEqualTo("name", name.trim())
                .limit(1)
                .get()
                .get();
            recordRead("Find tag by name: " + name, snapshot.size());

            if (snapshot.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(toTag(snapshot.getDocuments().get(0)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while finding tag by name: {}", name, ex);
            return Optional.empty();
        } catch (ExecutionException ex) {
            log.error("Failed to find tag by name: {}", name, ex);
            return Optional.empty();
        }
    }

    /**
     * Create a new tag.
     */
    public ItemTag createTag(String name, boolean predefined) {
        if (firestore.isEmpty() || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Tag name cannot be empty");
        }

        // Check if tag with same name already exists
        Optional<ItemTag> existing = findByName(name);
        if (existing.isPresent()) {
            throw new IllegalStateException("Tag with name '" + name + "' already exists");
        }

        try {
            Firestore db = firestore.get();
            DocumentReference docRef = db.collection(TAGS_COLLECTION).document();
            
            Instant now = Instant.now();
            Map<String, Object> data = new HashMap<>();
            data.put("name", name.trim());
            data.put("createdAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
            data.put("predefined", predefined);

            docRef.set(data).get();

            return ItemTag.builder()
                .id(docRef.getId())
                .name(name.trim())
                .createdAt(now)
                .predefined(predefined)
                .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while creating tag", ex);
            throw new RuntimeException("Failed to create tag", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to create tag", ex);
            throw new RuntimeException("Failed to create tag", ex);
        }
    }

    /**
     * Delete a tag. Only non-predefined tags can be deleted.
     */
    public void deleteTag(String id) {
        if (firestore.isEmpty() || !StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Tag ID cannot be empty");
        }

        Optional<ItemTag> existing = findById(id);
        if (existing.isEmpty()) {
            return; // Already deleted
        }

        if (existing.get().predefined()) {
            throw new IllegalStateException("Cannot delete predefined tag");
        }

        try {
            Firestore db = firestore.get();
            db.collection(TAGS_COLLECTION).document(id).delete().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while deleting tag", ex);
            throw new RuntimeException("Failed to delete tag", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to delete tag", ex);
            throw new RuntimeException("Failed to delete tag", ex);
        }
    }

    private ItemTag toTag(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        Map<String, Object> data = doc.getData();
        if (data == null) {
            return null;
        }

        String name = (String) data.get("name");
        if (!StringUtils.hasText(name)) {
            return null;
        }

        return ItemTag.builder()
            .id(doc.getId())
            .name(name)
            .createdAt(toInstant(data.get("createdAt")))
            .predefined(Boolean.TRUE.equals(data.get("predefined")))
            .build();
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        }
        return null;
    }

    private void recordRead(String description, long count) {
        if (readRecorder != null) {
            readRecorder.record(description, count);
        }
    }
}
