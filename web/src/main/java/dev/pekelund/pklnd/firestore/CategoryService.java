package dev.pekelund.pklnd.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for managing categories and their hierarchical structure.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);
    private static final String CATEGORIES_COLLECTION = "categories";

    private final Optional<Firestore> firestore;
    private final FirestoreReadRecorder readRecorder;

    public CategoryService(
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
     * List all categories ordered by name.
     */
    public List<Category> listCategories() {
        if (firestore.isEmpty()) {
            return List.of();
        }

        try {
            Firestore db = firestore.get();
            QuerySnapshot snapshot = db.collection(CATEGORIES_COLLECTION)
                .orderBy("name")
                .get()
                .get();
            recordRead("Load all categories", snapshot.size());

            List<Category> categories = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Category category = toCategory(doc);
                if (category != null) {
                    categories.add(category);
                }
            }
            return Collections.unmodifiableList(categories);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading categories from Firestore", ex);
            return List.of();
        } catch (ExecutionException ex) {
            log.error("Failed to load categories from Firestore", ex);
            return List.of();
        }
    }

    /**
     * Get categories organized in a hierarchical structure.
     * Returns a map where top-level categories map to their subcategories.
     */
    public Map<Category, List<Category>> getCategoriesHierarchy() {
        List<Category> allCategories = listCategories();
        
        Map<String, Category> categoryById = allCategories.stream()
            .collect(Collectors.toMap(Category::id, c -> c));

        Map<Category, List<Category>> hierarchy = new LinkedHashMap<>();
        
        List<Category> topLevel = allCategories.stream()
            .filter(Category::isTopLevel)
            .sorted(Comparator.comparing(Category::name))
            .toList();

        for (Category parent : topLevel) {
            List<Category> children = allCategories.stream()
                .filter(c -> parent.id().equals(c.parentId()))
                .sorted(Comparator.comparing(Category::name))
                .toList();
            hierarchy.put(parent, children);
        }

        return Collections.unmodifiableMap(hierarchy);
    }

    /**
     * Find a category by ID.
     */
    public Optional<Category> findById(String id) {
        if (firestore.isEmpty() || !StringUtils.hasText(id)) {
            return Optional.empty();
        }

        try {
            DocumentReference ref = firestore.get()
                .collection(CATEGORIES_COLLECTION)
                .document(id);
            DocumentSnapshot snapshot = ref.get().get();
            recordRead("Load category " + id, 1);

            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(toCategory(snapshot));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while loading category {} from Firestore", id, ex);
            return Optional.empty();
        } catch (ExecutionException ex) {
            log.error("Failed to load category {} from Firestore", id, ex);
            return Optional.empty();
        }
    }

    /**
     * Find a category by name (case-insensitive).
     */
    public Optional<Category> findByName(String name) {
        if (firestore.isEmpty() || !StringUtils.hasText(name)) {
            return Optional.empty();
        }

        try {
            Firestore db = firestore.get();
            QuerySnapshot snapshot = db.collection(CATEGORIES_COLLECTION)
                .whereEqualTo("name", name.trim())
                .limit(1)
                .get()
                .get();
            recordRead("Find category by name: " + name, snapshot.size());

            if (snapshot.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(toCategory(snapshot.getDocuments().get(0)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while finding category by name: {}", name, ex);
            return Optional.empty();
        } catch (ExecutionException ex) {
            log.error("Failed to find category by name: {}", name, ex);
            return Optional.empty();
        }
    }

    /**
     * Create a new category.
     */
    public Category createCategory(String name, String parentId, boolean predefined) {
        if (firestore.isEmpty() || !StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }

        // Check if category with same name already exists
        Optional<Category> existing = findByName(name);
        if (existing.isPresent()) {
            throw new IllegalStateException("Category with name '" + name + "' already exists");
        }

        // Validate parent exists if parentId is provided
        if (StringUtils.hasText(parentId)) {
            Optional<Category> parent = findById(parentId);
            if (parent.isEmpty()) {
                throw new IllegalArgumentException("Parent category not found: " + parentId);
            }
        }

        try {
            Firestore db = firestore.get();
            DocumentReference docRef = db.collection(CATEGORIES_COLLECTION).document();
            
            Instant now = Instant.now();
            Map<String, Object> data = new HashMap<>();
            data.put("name", name.trim());
            data.put("parentId", parentId);
            data.put("createdAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
            data.put("updatedAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));
            data.put("predefined", predefined);

            docRef.set(data).get();

            return Category.builder()
                .id(docRef.getId())
                .name(name.trim())
                .parentId(parentId)
                .createdAt(now)
                .updatedAt(now)
                .predefined(predefined)
                .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while creating category", ex);
            throw new RuntimeException("Failed to create category", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to create category", ex);
            throw new RuntimeException("Failed to create category", ex);
        }
    }

    /**
     * Update an existing category.
     */
    public Category updateCategory(String id, String newName, String newParentId) {
        if (firestore.isEmpty() || !StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Category ID cannot be empty");
        }

        Optional<Category> existing = findById(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Category not found: " + id);
        }

        Category current = existing.get();

        // Validate that we're not creating a circular reference
        if (StringUtils.hasText(newParentId) && !newParentId.equals(current.parentId())) {
            if (id.equals(newParentId)) {
                throw new IllegalArgumentException("Category cannot be its own parent");
            }
            // Check if newParentId is a descendant of this category
            if (isDescendant(id, newParentId)) {
                throw new IllegalArgumentException("Cannot set parent to a descendant category");
            }
        }

        try {
            Firestore db = firestore.get();
            DocumentReference docRef = db.collection(CATEGORIES_COLLECTION).document(id);
            
            Instant now = Instant.now();
            Map<String, Object> updates = new HashMap<>();
            if (StringUtils.hasText(newName)) {
                updates.put("name", newName.trim());
            }
            updates.put("parentId", newParentId);
            updates.put("updatedAt", Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano()));

            docRef.set(updates, SetOptions.merge()).get();

            return Category.builder()
                .id(id)
                .name(StringUtils.hasText(newName) ? newName.trim() : current.name())
                .parentId(newParentId)
                .createdAt(current.createdAt())
                .updatedAt(now)
                .predefined(current.predefined())
                .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while updating category", ex);
            throw new RuntimeException("Failed to update category", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to update category", ex);
            throw new RuntimeException("Failed to update category", ex);
        }
    }

    /**
     * Delete a category. Only non-predefined categories can be deleted.
     */
    public void deleteCategory(String id) {
        if (firestore.isEmpty() || !StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Category ID cannot be empty");
        }

        Optional<Category> existing = findById(id);
        if (existing.isEmpty()) {
            return; // Already deleted
        }

        if (existing.get().predefined()) {
            throw new IllegalStateException("Cannot delete predefined category");
        }

        // Check if category has subcategories
        List<Category> allCategories = listCategories();
        boolean hasChildren = allCategories.stream()
            .anyMatch(c -> id.equals(c.parentId()));
        
        if (hasChildren) {
            throw new IllegalStateException("Cannot delete category with subcategories");
        }

        try {
            Firestore db = firestore.get();
            db.collection(CATEGORIES_COLLECTION).document(id).delete().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while deleting category", ex);
            throw new RuntimeException("Failed to delete category", ex);
        } catch (ExecutionException ex) {
            log.error("Failed to delete category", ex);
            throw new RuntimeException("Failed to delete category", ex);
        }
    }

    /**
     * Check if targetId is a descendant of ancestorId.
     */
    private boolean isDescendant(String ancestorId, String targetId) {
        if (!StringUtils.hasText(targetId)) {
            return false;
        }
        
        Optional<Category> target = findById(targetId);
        if (target.isEmpty()) {
            return false;
        }

        String currentParentId = target.get().parentId();
        while (StringUtils.hasText(currentParentId)) {
            if (ancestorId.equals(currentParentId)) {
                return true;
            }
            Optional<Category> parent = findById(currentParentId);
            if (parent.isEmpty()) {
                break;
            }
            currentParentId = parent.get().parentId();
        }
        return false;
    }

    private Category toCategory(DocumentSnapshot doc) {
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

        return Category.builder()
            .id(doc.getId())
            .name(name)
            .parentId((String) data.get("parentId"))
            .createdAt(toInstant(data.get("createdAt")))
            .updatedAt(toInstant(data.get("updatedAt")))
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
