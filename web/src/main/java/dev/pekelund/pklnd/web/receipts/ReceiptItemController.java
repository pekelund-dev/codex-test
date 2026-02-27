package dev.pekelund.pklnd.web.receipts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.web.ReceiptOwnerResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ReceiptItemController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptItemController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern EAN_PATTERN = Pattern.compile("(\\d{8,14})");
    private static final Pattern QUANTITY_VALUE_PATTERN = Pattern.compile("([-+]?\\d+(?:[.,]\\d+)?)");
    private static final List<String> POSSIBLE_EAN_KEYS = List.of(
        "eanCode", "ean", "barcode", "barCode", "ean_code", "EAN", "gtin", "itemEan", "sku"
    );

    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;
    private final ReceiptScopeHelper scopeHelper;

    public ReceiptItemController(
        @Autowired(required = false) ReceiptExtractionService receiptExtractionService,
        ReceiptOwnerResolver receiptOwnerResolver,
        ReceiptScopeHelper scopeHelper
    ) {
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
        this.receiptOwnerResolver = receiptOwnerResolver;
        this.scopeHelper = scopeHelper;
    }

    @GetMapping("/receipts/items/{eanCode}")
    public String viewItemPurchases(
        @PathVariable("eanCode") String eanCode,
        @RequestParam(value = "sourceId", required = false) String sourceReceiptId,
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed receipts are not available.");
        }

        ReceiptViewScope scope = scopeHelper.resolveScope(scopeParam, authentication);
        ReceiptViewScope effectiveScope = scope;
        boolean canViewAll = scopeHelper.isAdmin(authentication);
        boolean viewingAll = scopeHelper.isViewingAll(scope, authentication);

        ReceiptOwner currentOwner = receiptOwnerResolver.resolve(authentication);
        if ((!viewingAll && currentOwner == null) || !StringUtils.hasText(eanCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
        }

        String trimmedEanCode = eanCode.trim();
        List<ReceiptExtractionService.ReceiptItemReference> itemReferences = receiptExtractionService.get()
            .findReceiptItemReferences(trimmedEanCode, viewingAll ? null : currentOwner, viewingAll);

        ReceiptExtractionService.ReceiptItemReference sourceReference = null;
        if (StringUtils.hasText(sourceReceiptId)) {
            sourceReference = findReferenceByReceiptId(itemReferences, sourceReceiptId);
            if (!viewingAll && canViewAll && sourceReference == null) {
                List<ReceiptExtractionService.ReceiptItemReference> expandedReferences = receiptExtractionService
                    .get()
                    .findReceiptItemReferences(trimmedEanCode, null, true);
                ReceiptExtractionService.ReceiptItemReference expandedSourceReference = findReferenceByReceiptId(
                    expandedReferences,
                    sourceReceiptId
                );
                if (expandedSourceReference != null
                    && !belongsToCurrentOwner(expandedSourceReference.ownerId(), currentOwner)) {
                    viewingAll = true;
                    effectiveScope = ReceiptViewScope.ALL;
                    itemReferences = expandedReferences;
                    sourceReference = expandedSourceReference;
                }
            }
        }

        ParsedReceipt sourceReceipt = null;
        String sourceReceiptIdentifier = StringUtils.hasText(sourceReceiptId) ? sourceReceiptId.trim() : null;
        String sourceReceiptName = sourceReference != null
            ? resolveReceiptDisplayName(
                sourceReference.receiptDisplayName(),
                sourceReference.receiptStoreName(),
                sourceReference.receiptObjectName()
            )
            : null;

        List<ItemPurchaseView> purchases = buildItemPurchasesFromReferences(trimmedEanCode, itemReferences);

        if (StringUtils.hasText(sourceReceiptIdentifier)) {
            boolean includedInReferences = purchases.stream()
                .anyMatch(purchase -> sourceReceiptIdentifier.equals(purchase.receiptId()));
            if (!includedInReferences) {
                sourceReceipt = receiptExtractionService
                    .get()
                    .findById(sourceReceiptIdentifier)
                    .orElse(null);
                if (sourceReceipt != null) {
                    List<ItemPurchaseView> fallback = buildItemPurchasesFromReceipts(trimmedEanCode,
                        List.of(sourceReceipt));
                    if (!fallback.isEmpty()) {
                        List<ItemPurchaseView> combined = new ArrayList<>(purchases.size() + fallback.size());
                        combined.addAll(purchases);
                        combined.addAll(fallback);
                        purchases = sortPurchases(combined);
                    }
                    if (!StringUtils.hasText(sourceReceiptName)) {
                        sourceReceiptName = resolveReceiptDisplayName(sourceReceipt);
                    }
                }
            }
        }

        if (purchases.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
        }

        String displayItemName = purchases.stream()
            .map(ItemPurchaseView::itemDisplayName)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse("EAN " + trimmedEanCode);
        String displayEanCode = purchases.get(0).itemEanCode();
        List<Map<String, Object>> priceHistory = buildPriceHistory(purchases);
        String priceHistoryJson = serializePriceHistory(priceHistory);
        boolean hasPriceHistory = !priceHistory.isEmpty();

        model.addAttribute("pageTitle", "Item: " + displayItemName);
        model.addAttribute("itemName", displayItemName);
        model.addAttribute("itemEan", displayEanCode);
        model.addAttribute("purchases", purchases);
        model.addAttribute("purchaseCount", purchases.size());
        model.addAttribute("priceHistoryJson", priceHistoryJson);
        model.addAttribute("hasPriceHistory", hasPriceHistory);
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("scopeParam", scopeHelper.toScopeParameter(effectiveScope));
        model.addAttribute("viewingAll", viewingAll);
        model.addAttribute("sourceReceiptId", sourceReceiptIdentifier);
        model.addAttribute("sourceReceiptName", sourceReceiptName);

        return "receipt-item";
    }

    private List<ItemPurchaseView> buildItemPurchasesFromReceipts(String targetEan, List<ParsedReceipt> receipts) {
        if (!StringUtils.hasText(targetEan) || receipts == null || receipts.isEmpty()) {
            return List.of();
        }

        String normalizedTarget = targetEan.trim();
        List<ItemPurchaseView> purchases = new ArrayList<>();

        for (ParsedReceipt receipt : receipts) {
            if (receipt == null) {
                continue;
            }

            String receiptDisplayName = resolveReceiptDisplayName(receipt);
            LocalDate parsedReceiptDate = parseReceiptDate(receipt.receiptDate());
            Instant sortInstant = determineSortInstant(parsedReceiptDate, receipt.updatedAt());
            String dateLabel = determineDateLabel(receipt.receiptDate(), receipt.updatedAt());
            String chartDate = parsedReceiptDate != null
                ? parsedReceiptDate.toString()
                : sortInstant != null
                    ? sortInstant.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    : null;

            for (Map<String, Object> item : receipt.displayItems()) {
                if (item == null || item.isEmpty()) {
                    continue;
                }

                String itemEan = extractItemEan(item);
                if (itemEan == null || !itemEan.equals(normalizedTarget)) {
                    continue;
                }

                String itemName = extractDisplayName(item.get("name"));

                BigDecimal totalPrice = resolveTotalPrice(item);
                BigDecimal unitPrice = resolveUnitPrice(item, totalPrice);
                String priceLabel = determinePriceLabel(item, unitPrice, totalPrice);
                BigDecimal resolvedPrice = unitPrice != null ? unitPrice : totalPrice;
                BigDecimal priceValue = resolvedPrice != null ? resolvedPrice.setScale(2, RoundingMode.HALF_UP) : null;

                purchases.add(new ItemPurchaseView(
                    itemName,
                    itemEan,
                    receipt.id(),
                    receiptDisplayName,
                    receipt.storeName(),
                    dateLabel,
                    priceLabel,
                    sortInstant,
                    chartDate,
                    priceValue
                ));
            }
        }

        return sortPurchases(purchases);
    }

    private List<ItemPurchaseView> buildItemPurchasesFromReferences(String targetEan,
        List<ReceiptExtractionService.ReceiptItemReference> references) {
        if (!StringUtils.hasText(targetEan) || references == null || references.isEmpty()) {
            return List.of();
        }

        String normalizedTarget = targetEan.trim();
        List<ItemPurchaseView> purchases = new ArrayList<>();

        for (ReceiptExtractionService.ReceiptItemReference reference : references) {
            if (reference == null) {
                continue;
            }

            Map<String, Object> item = reference.itemData();
            if (item == null || item.isEmpty()) {
                continue;
            }

            String itemEan = extractItemEan(item);
            if (itemEan == null || !itemEan.equals(normalizedTarget)) {
                continue;
            }

            String itemName = extractDisplayName(item.get("name"));
            BigDecimal totalPrice = resolveTotalPrice(item);
            BigDecimal unitPrice = resolveUnitPrice(item, totalPrice);
            String priceLabel = determinePriceLabel(item, unitPrice, totalPrice);
            BigDecimal resolvedPrice = unitPrice != null ? unitPrice : totalPrice;
            BigDecimal priceValue = resolvedPrice != null ? resolvedPrice.setScale(2, RoundingMode.HALF_UP) : null;

            LocalDate parsedReceiptDate = parseReceiptDate(reference.receiptDate());
            Instant sortInstant = determineSortInstant(parsedReceiptDate, reference.receiptUpdatedAt());
            String dateLabel = determineDateLabel(reference.receiptDate(), reference.receiptUpdatedAt());
            String chartDate = parsedReceiptDate != null
                ? parsedReceiptDate.toString()
                : sortInstant != null
                    ? sortInstant.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    : null;

            String receiptDisplayName = resolveReceiptDisplayName(
                reference.receiptDisplayName(),
                reference.receiptStoreName(),
                reference.receiptObjectName()
            );

            purchases.add(new ItemPurchaseView(
                itemName,
                itemEan,
                reference.receiptId(),
                receiptDisplayName,
                reference.receiptStoreName(),
                dateLabel,
                priceLabel,
                sortInstant,
                chartDate,
                priceValue
            ));
        }

        return sortPurchases(purchases);
    }

    private ReceiptExtractionService.ReceiptItemReference findReferenceByReceiptId(
        List<ReceiptExtractionService.ReceiptItemReference> references,
        String receiptId
    ) {
        if (references == null || references.isEmpty() || !StringUtils.hasText(receiptId)) {
            return null;
        }
        String trimmedId = receiptId.trim();
        return references.stream()
            .filter(reference -> trimmedId.equals(reference.receiptId()))
            .findFirst()
            .orElse(null);
    }

    private boolean belongsToCurrentOwner(String ownerId, ReceiptOwner owner) {
        if (!StringUtils.hasText(ownerId) || owner == null || !StringUtils.hasText(owner.id())) {
            return false;
        }
        return ownerId.equals(owner.id());
    }

    private List<ItemPurchaseView> sortPurchases(List<ItemPurchaseView> purchases) {
        if (purchases == null || purchases.isEmpty()) {
            return List.of();
        }
        List<ItemPurchaseView> sorted = new ArrayList<>(purchases);
        sorted.sort(Comparator.comparing(ItemPurchaseView::sortInstant,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(sorted);
    }

    private List<Map<String, Object>> buildPriceHistory(List<ItemPurchaseView> purchases) {
        if (purchases == null || purchases.isEmpty()) {
            return List.of();
        }

        return purchases.stream()
            .filter(entry -> entry.chartDate() != null && entry.priceValue() != null)
            .sorted(Comparator.comparing(ItemPurchaseView::sortInstant, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(entry -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", entry.chartDate());
                point.put("price", entry.priceValue());
                return point;
            })
            .toList();
    }

    private BigDecimal resolveTotalPrice(Map<String, Object> item) {
        BigDecimal totalPrice = parseBigDecimal(item.get("totalPrice"));
        if (totalPrice == null) {
            totalPrice = parseBigDecimal(item.get("displayTotalPrice"));
        }
        return totalPrice;
    }

    private BigDecimal resolveUnitPrice(Map<String, Object> item, BigDecimal totalPrice) {
        BigDecimal unitPrice = parseBigDecimal(item.get("unitPrice"));
        if (unitPrice == null) {
            unitPrice = parseBigDecimal(item.get("displayUnitPrice"));
        }
        if (unitPrice != null) {
            return unitPrice;
        }

        BigDecimal effectiveTotal = totalPrice != null ? totalPrice : resolveTotalPrice(item);
        if (effectiveTotal == null) {
            return null;
        }

        BigDecimal quantity = parseQuantityValue(item.get("quantity"));
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            quantity = parseQuantityValue(item.get("displayQuantity"));
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        try {
            return effectiveTotal.divide(quantity, 2, RoundingMode.HALF_UP);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private BigDecimal parseQuantityValue(Object rawQuantity) {
        if (rawQuantity == null) {
            return null;
        }
        String text = rawQuantity.toString().replace('\u00A0', ' ').trim();
        if (text.isEmpty()) {
            return null;
        }

        Matcher matcher = QUANTITY_VALUE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String numeric = matcher.group(1).replace(" ", "").replace(',', '.');
        try {
            return new BigDecimal(numeric);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String serializePriceHistory(List<Map<String, Object>> priceHistory) {
        if (priceHistory == null || priceHistory.isEmpty()) {
            return "[]";
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(priceHistory);
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Failed to serialize price history for item chart", ex);
            return "[]";
        }
    }

    private String extractItemEan(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        for (String key : POSSIBLE_EAN_KEYS) {
            Object raw = item.get(key);
            String normalized = extractEanCode(raw);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String extractEanCode(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        String text = rawValue.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        Matcher matcher = EAN_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        String digitsOnly = text.replaceAll("\\D+", "");
        if (digitsOnly.length() >= 8 && digitsOnly.length() <= 14) {
            return digitsOnly;
        }

        if (text.chars().allMatch(Character::isDigit) && text.length() >= 8 && text.length() <= 14) {
            return text;
        }

        return null;
    }

    private String extractDisplayName(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.replace('\u00A0', ' ').trim();
            if (normalized.isEmpty()) {
                return null;
            }
            normalized = normalized.replace(" ", "");
            normalized = normalized.replace(',', '.');
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private LocalDate parseReceiptDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Instant determineSortInstant(LocalDate receiptDate, Instant updatedAt) {
        if (receiptDate != null) {
            return receiptDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        return updatedAt;
    }

    private String determineDateLabel(String rawReceiptDate, Instant updatedAt) {
        if (StringUtils.hasText(rawReceiptDate)) {
            return rawReceiptDate;
        }
        if (updatedAt == null) {
            return null;
        }
        return updatedAt.toString();
    }

    private String determinePriceLabel(Map<String, Object> item, BigDecimal unitPrice, BigDecimal totalPrice) {
        Object displayUnit = item.get("displayUnitPrice");
        if (displayUnit != null) {
            return displayUnit.toString();
        }
        if (unitPrice != null) {
            return formatAmount(unitPrice);
        }
        Object rawUnit = item.get("unitPrice");
        if (rawUnit != null) {
            return rawUnit.toString();
        }

        Object displayTotal = item.get("displayTotalPrice");
        if (displayTotal != null) {
            return displayTotal.toString();
        }
        if (totalPrice != null) {
            return formatAmount(totalPrice);
        }
        Object rawTotal = item.get("totalPrice");
        return rawTotal != null ? rawTotal.toString() : null;
    }

    private String resolveReceiptDisplayName(ParsedReceipt receipt) {
        if (receipt == null) {
            return null;
        }
        String resolved = resolveReceiptDisplayName(receipt.displayName(), receipt.storeName(), receipt.objectName());
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        if (StringUtils.hasText(receipt.objectPath())) {
            return receipt.objectPath();
        }
        return receipt.id();
    }

    private String resolveReceiptDisplayName(String displayName, String storeName, String objectName) {
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        if (StringUtils.hasText(storeName)) {
            return storeName;
        }
        if (StringUtils.hasText(objectName)) {
            return objectName;
        }
        return null;
    }

    private record ItemPurchaseView(
        String itemDisplayName,
        String itemEanCode,
        String receiptId,
        String receiptDisplayName,
        String storeName,
        String dateLabel,
        String priceLabel,
        Instant sortInstant,
        String chartDate,
        BigDecimal priceValue
    ) {
    }
}
