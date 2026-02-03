package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.ItemCategorizationService.TaggedItemInfo;
import dev.pekelund.pklnd.firestore.ItemTag;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TagStatisticsService {

    private static final Pattern EAN_PATTERN = Pattern.compile("(\\d{8,14})");

    private final Optional<ItemCategorizationService> itemCategorizationService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;

    public TagStatisticsService(
        Optional<ItemCategorizationService> itemCategorizationService,
        Optional<ReceiptExtractionService> receiptExtractionService
    ) {
        this.itemCategorizationService = itemCategorizationService;
        this.receiptExtractionService = receiptExtractionService;
    }

    public boolean isEnabled() {
        return itemCategorizationService.isPresent()
            && itemCategorizationService.get().isEnabled()
            && receiptExtractionService.isPresent()
            && receiptExtractionService.get().isEnabled();
    }

    public Map<String, TagSummary> summarizeTags(List<ItemTag> tags) {
        if (!isEnabled() || tags == null || tags.isEmpty()) {
            return Map.of();
        }

        Map<String, ParsedReceipt> receiptsById = receiptExtractionService.get()
            .listAllReceipts()
            .stream()
            .filter(receipt -> receipt.id() != null)
            .collect(HashMap::new, (map, receipt) -> map.put(receipt.id(), receipt), HashMap::putAll);

        Map<String, TagSummary> summaries = new HashMap<>();
        for (ItemTag tag : tags) {
            if (tag == null || !StringUtils.hasText(tag.id())) {
                continue;
            }
            TagSummary summary = buildSummary(tag.id(), receiptsById);
            summaries.put(tag.id(), summary);
        }
        return Collections.unmodifiableMap(summaries);
    }

    private TagSummary buildSummary(String tagId, Map<String, ParsedReceipt> receiptsById) {
        List<TaggedItemInfo> taggedItems = itemCategorizationService.get().getItemsByTag(tagId);
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
            ParsedReceipt receipt = receiptsById.get(taggedItem.receiptId());
            if (receipt == null) {
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

    public record TagSummary(int itemCount, BigDecimal totalAmount, int storeCount) {
        public static TagSummary empty() {
            return new TagSummary(0, BigDecimal.ZERO, 0);
        }

        public String formattedTotalAmount() {
            return totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
