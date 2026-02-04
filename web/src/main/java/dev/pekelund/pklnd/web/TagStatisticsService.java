package dev.pekelund.pklnd.web;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import dev.pekelund.pklnd.firestore.FirestoreProperties;
import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.ItemCategorizationService.TaggedItemInfo;
import dev.pekelund.pklnd.firestore.ItemTag;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptOwnerMatcher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TagStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(TagStatisticsService.class);
    private static final Pattern EAN_PATTERN = Pattern.compile("(\\d{8,14})");

    private final FirestoreProperties firestoreProperties;
    private final Optional<Firestore> firestore;
    private final ReceiptOwnerResolver receiptOwnerResolver;
    private final Optional<ItemCategorizationService> itemCategorizationService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;

    public TagStatisticsService(
        FirestoreProperties firestoreProperties,
        ObjectProvider<Firestore> firestoreProvider,
        ReceiptOwnerResolver receiptOwnerResolver,
        Optional<ItemCategorizationService> itemCategorizationService,
        Optional<ReceiptExtractionService> receiptExtractionService
    ) {
        this.firestoreProperties = firestoreProperties;
        this.firestore = Optional.ofNullable(firestoreProvider.getIfAvailable());
        this.receiptOwnerResolver = receiptOwnerResolver;
        this.itemCategorizationService = itemCategorizationService;
        this.receiptExtractionService = receiptExtractionService;
    }

    public boolean isEnabled() {
        return itemCategorizationService.isPresent()
            && itemCategorizationService.get().isEnabled()
            && receiptExtractionService.isPresent()
            && receiptExtractionService.get().isEnabled();
    }

    public Map<String, TagSummary> summarizeTags(List<ItemTag> tags, Authentication authentication) {
        if (!isEnabled() || tags == null || tags.isEmpty()) {
            return Map.of();
        }

        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null || !StringUtils.hasText(owner.id())) {
            return Map.of();
        }
        String ownerId = owner.id();
        Map<String, TagSummary> summaries = new HashMap<>();
        Set<String> cacheKeys = new HashSet<>();
        for (ItemTag tag : tags) {
            if (tag == null || !StringUtils.hasText(tag.id())) {
                continue;
            }
            String cacheKey = buildCacheKey(ownerId, tag.id());
            if (StringUtils.hasText(cacheKey)) {
                cacheKeys.add(cacheKey);
            }
        }
        Map<String, Optional<Instant>> lastChangeAts = loadTagSummaryChangeAts(cacheKeys);
        Map<String, Optional<CachedTagSummary>> cachedSummaries = loadCachedSummaries(cacheKeys);
        Map<String, Optional<ParsedReceipt>> receiptCache = new HashMap<>();
        for (ItemTag tag : tags) {
            if (tag == null || !StringUtils.hasText(tag.id())) {
                continue;
            }
            String cacheKey = buildCacheKey(ownerId, tag.id());
            Optional<Instant> lastChangeAt = lastChangeAts.getOrDefault(cacheKey, Optional.empty());
            Optional<CachedTagSummary> cachedSummary = cachedSummaries.getOrDefault(cacheKey, Optional.empty());
            if (cachedSummary.isPresent()
                && (lastChangeAt.isEmpty() || !cachedSummary.get().computedAt().isBefore(lastChangeAt.get()))) {
                summaries.put(tag.id(), cachedSummary.get().summary());
                continue;
            }

            List<TaggedItemInfo> taggedItems = itemCategorizationService.get().getItemsByTag(tag.id());
            TagSummary summary = buildSummary(taggedItems, receiptCache, owner);
            summaries.put(tag.id(), summary);
            storeTagSummary(cacheKey, tag.id(), ownerId, summary, lastChangeAt);
        }
        return Collections.unmodifiableMap(summaries);
    }

    private TagSummary buildSummary(List<TaggedItemInfo> taggedItems,
                                    Map<String, Optional<ParsedReceipt>> receiptCache,
                                    ReceiptOwner owner) {
        if (taggedItems.isEmpty()) {
            return TagSummary.empty();
        }

        int itemCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        Set<String> stores = new HashSet<>();

        for (TaggedItemInfo taggedItem : taggedItems) {
            if (taggedItem == null) {
                continue;
            }
            ParsedReceipt receipt = resolveReceipt(taggedItem.receiptId(), receiptCache);
            if (receipt == null || !isReceiptOwnedBy(receipt, owner)) {
                continue;
            }

            BigDecimal itemTotal = resolveItemTotal(receipt, taggedItem);
            if (itemTotal != null) {
                totalAmount = totalAmount.add(itemTotal);
            }
            itemCount++;

            String storeName = receipt.storeName();
            if (StringUtils.hasText(storeName)) {
                stores.add(storeName.trim());
            }
        }

        return new TagSummary(itemCount, totalAmount, stores.size());
    }

    private Map<String, Optional<CachedTagSummary>> loadCachedSummaries(Set<String> cacheKeys) {
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            return Map.of();
        }
        if (!isCacheEnabled()) {
            return Map.of();
        }

        try {
            List<DocumentReference> references = toDocumentReferences(
                firestoreProperties.getTagSummariesCollection(),
                cacheKeys
            );
            if (references.isEmpty()) {
                return Map.of();
            }
            var future = firestore.get().getAll(references.toArray(DocumentReference[]::new));
            List<DocumentSnapshot> snapshots = future.get();
            Map<String, Optional<CachedTagSummary>> result = new HashMap<>();
            for (DocumentSnapshot snapshot : snapshots) {
                if (snapshot == null) {
                    continue;
                }
                String key = snapshot.getId();
                if (!snapshot.exists() || snapshot.getData() == null) {
                    result.put(key, Optional.empty());
                    continue;
                }
                Instant computedAt = toInstant(snapshot.get("computedAt"));
                if (computedAt == null) {
                    result.put(key, Optional.empty());
                    continue;
                }
                int itemCount = parseInt(snapshot.get("itemCount"));
                int storeCount = parseInt(snapshot.get("storeCount"));
                BigDecimal totalAmount = parseAmount(snapshot.get("totalAmount"));
                TagSummary summary = new TagSummary(itemCount, totalAmount, storeCount);
                result.put(key, Optional.of(new CachedTagSummary(summary, computedAt)));
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to load cached summaries", ex);
            return Map.of();
        }
    }

    private Map<String, Optional<Instant>> loadTagSummaryChangeAts(Set<String> cacheKeys) {
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            return Map.of();
        }
        if (!isCacheEnabled()) {
            return Map.of();
        }

        try {
            List<DocumentReference> references = toDocumentReferences(
                firestoreProperties.getTagSummaryMetaCollection(),
                cacheKeys
            );
            if (references.isEmpty()) {
                return Map.of();
            }
            var future = firestore.get().getAll(references.toArray(DocumentReference[]::new));
            List<DocumentSnapshot> snapshots = future.get();
            Map<String, Optional<Instant>> result = new HashMap<>();
            for (DocumentSnapshot snapshot : snapshots) {
                if (snapshot == null) {
                    continue;
                }
                if (!snapshot.exists()) {
                    result.put(snapshot.getId(), Optional.empty());
                    continue;
                }
                result.put(snapshot.getId(), Optional.ofNullable(toInstant(snapshot.get("updatedAt"))));
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to load tag summary change timestamps", ex);
            return Map.of();
        }
    }

    private List<DocumentReference> toDocumentReferences(String collection, Set<String> cacheKeys) {
        if (firestore.isEmpty() || !StringUtils.hasText(collection) || cacheKeys == null || cacheKeys.isEmpty()) {
            return List.of();
        }
        List<DocumentReference> references = new ArrayList<>();
        for (String cacheKey : cacheKeys) {
            if (StringUtils.hasText(cacheKey)) {
                references.add(firestore.get().collection(collection).document(cacheKey));
            }
        }
        return references;
    }

    private void storeTagSummary(String cacheKey,
                                 String tagId,
                                 String ownerId,
                                 TagSummary summary,
                                 Optional<Instant> lastChangeAt) {
        if (!isCacheEnabled(cacheKey)) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("tagId", tagId);
        if (StringUtils.hasText(ownerId)) {
            payload.put("ownerId", ownerId);
        }
        payload.put("itemCount", summary.itemCount());
        payload.put("storeCount", summary.storeCount());
        payload.put("totalAmount", summary.totalAmount().toPlainString());
        payload.put("computedAt", Timestamp.now());
        lastChangeAt.map(instant -> Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano()))
            .ifPresent(timestamp -> payload.put("lastChangeAt", timestamp));

        try {
            firestore.get()
                .collection(firestoreProperties.getTagSummariesCollection())
                .document(cacheKey)
                .set(payload);
        } catch (Exception ex) {
            log.warn("Failed to store tag summary for key {}", cacheKey, ex);
        }
    }

    private boolean isCacheEnabled() {
        return firestore.isPresent()
            && firestoreProperties.isEnabled()
            && StringUtils.hasText(firestoreProperties.getTagSummariesCollection())
            && StringUtils.hasText(firestoreProperties.getTagSummaryMetaCollection());
    }

    private boolean isCacheEnabled(String cacheKey) {
        return isCacheEnabled() && StringUtils.hasText(cacheKey);
    }

    private String buildCacheKey(String ownerId, String tagId) {
        if (!StringUtils.hasText(ownerId) || !StringUtils.hasText(tagId)) {
            return null;
        }
        return ownerId + ":" + tagId;
    }

    private ParsedReceipt resolveReceipt(String receiptId, Map<String, Optional<ParsedReceipt>> receiptCache) {
        if (!StringUtils.hasText(receiptId)) {
            return null;
        }
        if (receiptCache.containsKey(receiptId)) {
            return receiptCache.get(receiptId).orElse(null);
        }

        Optional<ParsedReceipt> receipt = receiptExtractionService.get().findById(receiptId);
        receiptCache.put(receiptId, receipt);
        return receipt.orElse(null);
    }

    private boolean isReceiptOwnedBy(ParsedReceipt receipt, ReceiptOwner owner) {
        if (receipt == null) {
            return false;
        }
        return ReceiptOwnerMatcher.belongsToCurrentOwner(receipt.owner(), owner);
    }

    private BigDecimal resolveItemTotal(ParsedReceipt receipt, TaggedItemInfo taggedItem) {
        List<Map<String, Object>> items = receipt.displayItems();
        if (items == null || items.isEmpty()) {
            return null;
        }

        Integer index = parseIndex(taggedItem.itemIndex());
        if (index != null && index >= 0 && index < items.size()) {
            return extractPrice(items.get(index));
        }

        if (!StringUtils.hasText(taggedItem.itemEan())) {
            return null;
        }

        String targetEan = normalizeEan(taggedItem.itemEan());
        if (!StringUtils.hasText(targetEan)) {
            return null;
        }

        return items.stream()
            .filter(item -> targetEan.equals(normalizeEan(extractEan(item))))
            .map(this::extractPrice)
            .filter(price -> price != null)
            .findFirst()
            .orElse(null);
    }

    private Integer parseIndex(String rawIndex) {
        if (!StringUtils.hasText(rawIndex)) {
            return null;
        }
        try {
            return Integer.parseInt(rawIndex.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractEan(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }

        Object normalized = item.get("normalizedEan");
        if (normalized != null) {
            return normalized.toString();
        }

        Object eanCode = item.get("eanCode");
        if (eanCode != null) {
            return eanCode.toString();
        }

        Object ean = item.get("ean");
        if (ean != null) {
            return ean.toString();
        }

        return null;
    }

    private String normalizeEan(String rawEan) {
        if (!StringUtils.hasText(rawEan)) {
            return null;
        }
        Matcher matcher = EAN_PATTERN.matcher(rawEan.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private BigDecimal extractPrice(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }

        BigDecimal totalPrice = parseBigDecimal(item.get("totalPrice"));
        if (totalPrice != null) {
            return totalPrice;
        }
        return parseBigDecimal(item.get("displayTotalPrice"));
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            try {
                return new BigDecimal(string.replace(',', '.'));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal parseAmount(Object value) {
        BigDecimal parsed = parseBigDecimal(value);
        return parsed != null ? parsed : BigDecimal.ZERO;
    }

    private int parseInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        return 0;
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        }
        return null;
    }

    private record CachedTagSummary(TagSummary summary, Instant computedAt) {}

    public record TagSummary(int itemCount, BigDecimal totalAmount, int storeCount) {
        public static TagSummary empty() {
            return new TagSummary(0, BigDecimal.ZERO, 0);
        }

        public String formattedTotalAmount() {
            return totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
