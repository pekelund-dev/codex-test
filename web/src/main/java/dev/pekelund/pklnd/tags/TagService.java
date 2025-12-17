package dev.pekelund.pklnd.tags;

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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TagService {

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

    private final ConcurrentMap<String, ConcurrentMap<String, TagDefinition>> userTags = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Set<String>>> userEanTags = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public List<TagView> listTagOptions(String ownerId, Locale locale) {
        ConcurrentMap<String, TagDefinition> tags = userTags.get(normalizeOwner(ownerId));
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

    public TagDefinition createOrUpdateTag(String ownerId, String tagId, Map<String, String> translations) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null) {
            throw new IllegalArgumentException("Owner is required for tags.");
        }

        String id = StringUtils.hasText(tagId) ? tagId.trim() : UUID.randomUUID().toString();
        Map<String, String> normalizedTranslations = normalizeTranslations(translations);
        ConcurrentMap<String, TagDefinition> tags = userTags.computeIfAbsent(
            normalizedOwner,
            key -> new ConcurrentHashMap<>()
        );
        String color = resolveColor(tags, id);
        TagDefinition definition = new TagDefinition(id, normalizedTranslations, color);
        tags.put(definition.id(), definition);
        return definition;
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
        TagDefinition tag = createOrUpdateTag(normalizedOwner, tagId, translations);
        ConcurrentMap<String, Set<String>> eanTags = userEanTags.computeIfAbsent(
            normalizedOwner,
            key -> new ConcurrentHashMap<>()
        );
        eanTags.computeIfAbsent(normalizedEan, key -> new LinkedHashSet<>()).add(tag.id());
        return tag;
    }

    public List<TagView> tagsForEan(String ownerId, String ean, Locale locale) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null) {
            return List.of();
        }
        if (!StringUtils.hasText(ean)) {
            return List.of();
        }
        ConcurrentMap<String, TagDefinition> tags = userTags.get(normalizedOwner);
        ConcurrentMap<String, Set<String>> eanTags = userEanTags.get(normalizedOwner);
        if (tags == null || eanTags == null) {
            return List.of();
        }

        Set<String> tagIds = eanTags.getOrDefault(ean.trim(), Set.of());
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

    public Map<String, Set<String>> tagMappings(String ownerId) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null) {
            return Map.of();
        }
        ConcurrentMap<String, Set<String>> eanTags = userEanTags.get(normalizedOwner);
        if (eanTags == null || eanTags.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : eanTags.entrySet()) {
            snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public Optional<TagDefinition> findTag(String ownerId, String tagId) {
        String normalizedOwner = normalizeOwner(ownerId);
        if (normalizedOwner == null || !StringUtils.hasText(tagId)) {
            return Optional.empty();
        }
        ConcurrentMap<String, TagDefinition> tags = userTags.get(normalizedOwner);
        if (tags == null) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(tagId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tags.get(tagId));
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
}
