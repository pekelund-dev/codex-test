package dev.pekelund.pklnd.tags;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import dev.pekelund.pklnd.firestore.FirestoreProperties;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private static final List<String> COLOR_PALETTE = List.of(
        "#2f6bc6",
        "#2ca58d",
        "#e67e22",
        "#9b59b6",
        "#e74c3c",
        "#16a085",
        "#34495e",
        "#f1c40f",
        "#7f8c8d",
        "#c0392b"
    );

    private static final String OWNER_FIELD = "ownerId";
    private static final String TAG_ID_FIELD = "tagId";
    private static final String TRANSLATIONS_FIELD = "translations";
    private static final String COLOR_FIELD = "color";
    private static final String EAN_FIELD = "ean";
    private static final String TAG_IDS_FIELD = "tagIds";

    private final ConcurrentMap<String, ConcurrentMap<String, TagDefinition>> userTags = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Set<String>>> userEanTags = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Optional<Firestore> firestore;
    private final FirestoreProperties properties;

    public TagService(ObjectProvider<Firestore> firestoreProvider, FirestoreProperties properties) {
        this(Optional.ofNullable(firestoreProvider.getIfAvailable()), properties);
    }

    public TagService() {
        this(Optional.empty(), new FirestoreProperties());
    }

    TagService(Optional<Firestore> firestore, FirestoreProperties properties) {
        this.firestore = firestore != null ? firestore : Optional.empty();
        this.properties = properties != null ? properties : new FirestoreProperties();
    }

    public List<TagView> listTagOptions(String ownerId, Locale locale) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null) {
            return List.of();
        }
        if (isFirestoreEnabled()) {
            return listTagOptionsFromFirestore(normalizedOwner, locale);
        }
        return listTagOptionsInMemory(normalizedOwner, locale);
    }

    public TagDefinition createOrUpdateTag(String ownerId, String tagId, Map<String, String> translations) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null) {
            throw new IllegalArgumentException("Owner is required for tags.");
        }

        if (isFirestoreEnabled()) {
            return createOrUpdateTagInFirestore(normalizedOwner, tagId, translations, null);
        }
        return createOrUpdateTagInMemory(normalizedOwner, tagId, translations);
    }

    public TagDefinition assignTagToEan(String ownerId, String ean, String tagId, Map<String, String> translations) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null) {
            throw new IllegalArgumentException("Owner is required when assigning a tag.");
        }
        if (!StringUtils.hasText(ean)) {
            throw new IllegalArgumentException("EAN is required when assigning a tag.");
        }
        String normalizedEan = ean.trim();

        if (isFirestoreEnabled()) {
            Map<String, TagDefinition> existingTags = loadTagsFromFirestore(normalizedOwner);
            TagDefinition tag = createOrUpdateTagInFirestore(normalizedOwner, tagId, translations, existingTags);
            saveEanMapping(normalizedOwner, normalizedEan, tag.id());
            return tag;
        }

        TagDefinition tag = createOrUpdateTagInMemory(normalizedOwner, tagId, translations);
        ConcurrentMap<String, Set<String>> eanTags = userEanTags.computeIfAbsent(
            normalizedOwner,
            key -> new ConcurrentHashMap<>()
        );
        eanTags.computeIfAbsent(normalizedEan, key -> new LinkedHashSet<>()).add(tag.id());
        return tag;
    }

    public List<TagView> tagsForEan(String ownerId, String ean, Locale locale) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null || !StringUtils.hasText(ean)) {
            return List.of();
        }

        if (isFirestoreEnabled()) {
            return tagsForEanFromFirestore(normalizedOwner, ean.trim(), locale);
        }
        return tagsForEanInMemory(normalizedOwner, ean.trim(), locale);
    }

    public Map<String, Set<String>> tagMappings(String ownerId) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null) {
            return Map.of();
        }
        if (isFirestoreEnabled()) {
            return tagMappingsFromFirestore(normalizedOwner);
        }
        return tagMappingsInMemory(normalizedOwner);
    }

    public Optional<TagDefinition> findTag(String ownerId, String tagId) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null || !StringUtils.hasText(tagId)) {
            return Optional.empty();
        }

        if (isFirestoreEnabled()) {
            return findTagFromFirestore(normalizedOwner, tagId.trim());
        }
        ConcurrentMap<String, TagDefinition> tags = userTags.get(normalizedOwner);
        if (tags == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tags.get(tagId));
    }

    private List<TagView> listTagOptionsInMemory(String ownerId, Locale locale) {
        ConcurrentMap<String, TagDefinition> tags = userTags.get(ownerId);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        return tags
            .values()
            .stream()
            .sorted((a, b) -> compareNames(a, b, locale))
            .map(tag -> new TagView(tag.id(), resolveName(tag, locale), tag.color()))
            .toList();
    }

    private List<TagView> listTagOptionsFromFirestore(String ownerId, Locale locale) {
        Map<String, TagDefinition> tags = loadTagsFromFirestore(ownerId);
        if (tags.isEmpty()) {
            return List.of();
        }

        return tags
            .values()
            .stream()
            .sorted((a, b) -> compareNames(a, b, locale))
            .map(tag -> new TagView(tag.id(), resolveName(tag, locale), tag.color()))
            .toList();
    }

    private TagDefinition createOrUpdateTagInMemory(String ownerId, String tagId, Map<String, String> translations) {
        String id = StringUtils.hasText(tagId) ? tagId.trim() : UUID.randomUUID().toString();
        ConcurrentMap<String, TagDefinition> tags = userTags.computeIfAbsent(ownerId, key -> new ConcurrentHashMap<>());
        TagDefinition existing = tags.get(id);
        Map<String, String> mergedTranslations = mergeTranslations(existing, translations);
        String color = resolveColor(tags, id);
        TagDefinition definition = new TagDefinition(id, mergedTranslations, color);
        tags.put(definition.id(), definition);
        return definition;
    }

    private TagDefinition createOrUpdateTagInFirestore(
        String ownerId,
        String tagId,
        Map<String, String> translations,
        Map<String, TagDefinition> existingTags
    ) {
        Map<String, TagDefinition> tags = existingTags != null ? new LinkedHashMap<>(existingTags) : loadTagsFromFirestore(ownerId);
        String id = StringUtils.hasText(tagId) ? tagId.trim() : UUID.randomUUID().toString();
        TagDefinition existing = tags.get(id);
        Map<String, String> mergedTranslations = mergeTranslations(existing, translations);
        String color = resolveColor(tags, id);
        TagDefinition definition = new TagDefinition(id, mergedTranslations, color);
        persistTag(ownerId, definition);
        return definition;
    }

    private List<TagView> tagsForEanInMemory(String ownerId, String ean, Locale locale) {
        ConcurrentMap<String, TagDefinition> tags = userTags.get(ownerId);
        ConcurrentMap<String, Set<String>> eanTags = userEanTags.get(ownerId);
        if (tags == null || eanTags == null) {
            return List.of();
        }

        Set<String> tagIds = eanTags.getOrDefault(ean, Set.of());
        if (tagIds.isEmpty()) {
            return List.of();
        }
        List<TagView> resolved = new ArrayList<>();
        for (String id : tagIds) {
            TagDefinition definition = tags.get(id);
            if (definition == null) {
                continue;
            }
            resolved.add(new TagView(definition.id(), resolveName(definition, locale), definition.color()));
        }
        resolved.sort((a, b) -> compareNames(a.name(), b.name()));
        return Collections.unmodifiableList(resolved);
    }

    private List<TagView> tagsForEanFromFirestore(String ownerId, String ean, Locale locale) {
        Map<String, TagDefinition> tags = loadTagsFromFirestore(ownerId);
        if (tags.isEmpty()) {
            return List.of();
        }

        Set<String> tagIds = loadTagIdsForEan(ownerId, ean);
        if (tagIds.isEmpty()) {
            return List.of();
        }

        List<TagView> resolved = new ArrayList<>();
        for (String id : tagIds) {
            TagDefinition definition = tags.get(id);
            if (definition == null) {
                continue;
            }
            resolved.add(new TagView(definition.id(), resolveName(definition, locale), definition.color()));
        }
        resolved.sort((a, b) -> compareNames(a.name(), b.name()));
        return Collections.unmodifiableList(resolved);
    }

    private Map<String, Set<String>> tagMappingsInMemory(String ownerId) {
        ConcurrentMap<String, Set<String>> eanTags = userEanTags.get(ownerId);
        if (eanTags == null || eanTags.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : eanTags.entrySet()) {
            snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    private Map<String, Set<String>> tagMappingsFromFirestore(String ownerId) {
        try {
            QuerySnapshot snapshot = firestore
                .get()
                .collection(properties.getTagMappingsCollection())
                .whereEqualTo(OWNER_FIELD, ownerId)
                .get()
                .get();
            if (snapshot == null || snapshot.isEmpty()) {
                return Map.of();
            }
            Map<String, Set<String>> mappings = new LinkedHashMap<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                String normalizedEan = document.getString(EAN_FIELD);
                if (!StringUtils.hasText(normalizedEan)) {
                    continue;
                }
                mappings.put(normalizedEan, extractTagIds(document));
            }
            return Collections.unmodifiableMap(mappings);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TagAccessException("Interrupted while loading tag mappings from Firestore.", ex);
        } catch (ExecutionException ex) {
            throw new TagAccessException("Failed to load tag mappings from Firestore.", ex);
        }
    }

    private Optional<TagDefinition> findTagFromFirestore(String ownerId, String tagId) {
        DocumentReference reference = firestore
            .get()
            .collection(properties.getTagsCollection())
            .document(buildTagDocumentId(ownerId, tagId));
        try {
            DocumentSnapshot snapshot = reference.get().get();
            if (snapshot == null || !snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(toTagDefinition(snapshot));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TagAccessException("Interrupted while reading tag from Firestore.", ex);
        } catch (ExecutionException ex) {
            throw new TagAccessException("Failed to read tag from Firestore.", ex);
        }
    }

    private Map<String, TagDefinition> loadTagsFromFirestore(String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            return Map.of();
        }
        try {
            QuerySnapshot snapshot = firestore
                .get()
                .collection(properties.getTagsCollection())
                .whereEqualTo(OWNER_FIELD, ownerId)
                .get()
                .get();
            Map<String, TagDefinition> tags = new LinkedHashMap<>();
            if (snapshot != null) {
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    TagDefinition definition = toTagDefinition(document);
                    if (definition == null) {
                        continue;
                    }
                    tags.put(definition.id(), definition);
                }
            }
            return tags;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TagAccessException("Interrupted while loading tags from Firestore.", ex);
        } catch (ExecutionException ex) {
            throw new TagAccessException("Failed to load tags from Firestore.", ex);
        }
    }

    private void persistTag(String ownerId, TagDefinition definition) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(OWNER_FIELD, ownerId);
        data.put(TAG_ID_FIELD, definition.id());
        data.put(TRANSLATIONS_FIELD, definition.translations());
        data.put(COLOR_FIELD, definition.color());
        data.put("updatedAt", FieldValue.serverTimestamp());

        DocumentReference reference = firestore
            .get()
            .collection(properties.getTagsCollection())
            .document(buildTagDocumentId(ownerId, definition.id()));
        try {
            reference.set(data, SetOptions.merge()).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TagAccessException("Interrupted while saving tag to Firestore.", ex);
        } catch (ExecutionException ex) {
            throw new TagAccessException("Failed to save tag to Firestore.", ex);
        }
    }

    private void saveEanMapping(String ownerId, String normalizedEan, String tagId) {
        DocumentReference reference = firestore
            .get()
            .collection(properties.getTagMappingsCollection())
            .document(buildMappingDocumentId(ownerId, normalizedEan));
        try {
            DocumentSnapshot snapshot = reference.get().get();
            Set<String> tagIds = extractTagIds(snapshot);
            tagIds.add(tagId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put(OWNER_FIELD, ownerId);
            data.put(EAN_FIELD, normalizedEan);
            data.put(TAG_IDS_FIELD, tagIds);
            data.put("updatedAt", FieldValue.serverTimestamp());
            reference.set(data, SetOptions.merge()).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TagAccessException("Interrupted while saving tag mapping to Firestore.", ex);
        } catch (ExecutionException ex) {
            throw new TagAccessException("Failed to save tag mapping to Firestore.", ex);
        }
    }

    private TagDefinition toTagDefinition(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }
        String tagId = snapshot.getString(TAG_ID_FIELD);
        if (!StringUtils.hasText(tagId)) {
            tagId = parseTagId(snapshot.getId());
        }
        if (!StringUtils.hasText(tagId)) {
            return null;
        }
        Map<String, String> translations = normalizeTranslations(snapshot.get(TRANSLATIONS_FIELD, Map.class));
        String color = snapshot.getString(COLOR_FIELD);
        return new TagDefinition(tagId, translations, color);
    }

    private Set<String> loadTagIdsForEan(String ownerId, String ean) {
        DocumentReference reference = firestore
            .get()
            .collection(properties.getTagMappingsCollection())
            .document(buildMappingDocumentId(ownerId, ean));
        try {
            DocumentSnapshot snapshot = reference.get().get();
            if (snapshot == null || !snapshot.exists()) {
                return Set.of();
            }
            return extractTagIds(snapshot);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TagAccessException("Interrupted while loading tag mapping from Firestore.", ex);
        } catch (ExecutionException ex) {
            throw new TagAccessException("Failed to load tag mapping from Firestore.", ex);
        }
    }

    private Set<String> extractTagIds(DocumentSnapshot snapshot) {
        if (snapshot == null) {
            return new LinkedHashSet<>();
        }
        List<String> tagIds = snapshot.get(TAG_IDS_FIELD, List.class);
        if (tagIds == null) {
            return new LinkedHashSet<>();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (Object tagId : tagIds) {
            if (tagId == null) {
                continue;
            }
            String text = tagId.toString();
            if (StringUtils.hasText(text)) {
                normalized.add(text.trim());
            }
        }
        return normalized;
    }

    private String parseTagId(String documentId) {
        if (!StringUtils.hasText(documentId)) {
            return null;
        }
        int separator = documentId.indexOf('#');
        if (separator < 0 || separator == documentId.length() - 1) {
            return documentId;
        }
        return documentId.substring(separator + 1);
    }

    private Map<String, String> mergeTranslations(TagDefinition existing, Map<String, String> translations) {
        Map<String, String> normalized = normalizeTranslations(translations);
        if (existing == null || existing.translations().isEmpty()) {
            return normalized;
        }
        Map<String, String> merged = new LinkedHashMap<>(existing.translations());
        merged.putAll(normalized);
        return merged;
    }

    private Map<String, String> normalizeTranslations(Map<String, String> translations) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (translations == null || translations.isEmpty()) {
            return normalized;
        }
        translations.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
                return;
            }
            normalized.put(key.trim().toLowerCase(Locale.ROOT), value.trim());
        });
        return normalized;
    }

    private String resolveColor(Map<String, TagDefinition> tags, String tagId) {
        TagDefinition existing = tags.get(tagId);
        if (existing != null && StringUtils.hasText(existing.color())) {
            return existing.color();
        }

        Set<String> usedColors = tags.values().stream().map(TagDefinition::color).collect(LinkedHashSet::new, Set::add, Set::addAll);
        for (String candidate : COLOR_PALETTE) {
            if (!usedColors.contains(candidate)) {
                return candidate;
            }
        }

        String candidate;
        do {
            candidate = String.format("#%06x", random.nextInt(0xFFFFFF + 1));
        } while (usedColors.contains(candidate));
        return candidate;
    }

    private String resolveName(TagDefinition definition, Locale locale) {
        String name = definition.displayName(locale);
        if (StringUtils.hasText(name)) {
            return name;
        }
        return definition.id();
    }

    private int compareNames(TagDefinition a, TagDefinition b, Locale locale) {
        return compareNames(resolveName(a, locale), resolveName(b, locale));
    }

    private int compareNames(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareToIgnoreCase(right);
    }

    private String normalizeOwner(String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            return null;
        }
        return ownerId.trim();
    }

    private boolean isFirestoreEnabled() {
        return firestore.isPresent() && properties.isEnabled();
    }

    private String buildTagDocumentId(String ownerId, String tagId) {
        return ownerId + "#" + tagId;
    }

    private String buildMappingDocumentId(String ownerId, String ean) {
        return ownerId + "#" + ean;
    }
}
