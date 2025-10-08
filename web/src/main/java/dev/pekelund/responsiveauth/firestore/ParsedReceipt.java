package dev.pekelund.responsiveauth.firestore;

import dev.pekelund.responsiveauth.storage.ReceiptOwner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ParsedReceipt(
    String id,
    String bucket,
    String objectName,
    String objectPath,
    ReceiptOwner owner,
    String status,
    String statusMessage,
    Instant updatedAt,
    Map<String, Object> general,
    List<Map<String, Object>> items,
    List<Map<String, Object>> vats,
    List<Map<String, Object>> generalDiscounts,
    List<Map<String, Object>> errors,
    String rawText,
    String rawResponse,
    String error
) {

    private static final Pattern QUANTITY_PATTERN = Pattern.compile("([-+]?\\d+(?:[.,]\\d+)?)\\s*(\\p{L}+)?");
    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.01");

    public ParsedReceipt {
        general = copyOfMap(general);
        items = copyOfMapList(items);
        vats = copyOfMapList(vats);
        generalDiscounts = copyOfMapList(generalDiscounts);
        errors = copyOfMapList(errors);
    }

    public String displayName() {
        String fileName = fileName();
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        if (objectName != null && !objectName.isBlank()) {
            return objectName;
        }
        return null;
    }

    public String fileName() {
        return valueFromGeneral("fileName");
    }

    public String storeName() {
        return valueFromGeneral("storeName");
    }

    public String receiptDate() {
        return valueFromGeneral("receiptDate");
    }

    public String totalAmount() {
        return valueFromGeneral("totalAmount");
    }

    public String formattedTotalAmount() {
        BigDecimal amount = numericValueFromGeneral("totalAmount");
        return formatAmount(amount);
    }

    public String format() {
        return valueFromGeneral("format");
    }

    public String statusBadgeClass() {
        if (status == null || status.isBlank()) {
            return "bg-secondary-subtle text-secondary";
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "COMPLETED" -> "bg-success-subtle text-success";
            case "FAILED" -> "bg-danger-subtle text-danger";
            case "PROCESSING", "RUNNING", "PENDING" -> "bg-info-subtle text-info";
            default -> "bg-secondary-subtle text-secondary";
        };
    }

    private String valueFromGeneral(String key) {
        Object value = general.get(key);
        return value != null ? value.toString() : null;
    }

    public List<Map<String, Object>> displayItems() {
        if (items.isEmpty()) {
            return items;
        }

        List<Map<String, Object>> normalized = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            if (item == null || item.isEmpty()) {
                normalized.add(Map.of());
                continue;
            }

            Map<String, Object> copy = new LinkedHashMap<>(item);
            BigDecimal originalUnitPrice = parseBigDecimal(item.get("unitPrice"));
            BigDecimal totalPrice = parseBigDecimal(item.get("totalPrice"));
            QuantityParts parts = parseQuantity(item.get("quantity"));

            boolean starItem = isStarItem(item.get("name"));
            BigDecimal quantityValue = parts.value();
            String unit = parts.unit();
            boolean priceMismatch = hasPriceMismatch(quantityValue, originalUnitPrice, totalPrice);

            BigDecimal displayUnitPrice = originalUnitPrice;
            String quantityDisplay;

            if (isWeightUnit(unit)) {
                quantityDisplay = formatWeight(quantityValue != null ? quantityValue : deriveWeight(originalUnitPrice, totalPrice));
            } else if (priceMismatch && !starItem) {
                BigDecimal weight = deriveWeight(originalUnitPrice, totalPrice);
                quantityDisplay = formatWeight(weight);
            } else {
                quantityDisplay = formatCount(quantityValue, unit);
            }

            if (priceMismatch && starItem) {
                BigDecimal recalculated = recalculateUnitPrice(totalPrice, quantityValue);
                if (recalculated != null) {
                    displayUnitPrice = recalculated;
                }
                if (quantityDisplay == null) {
                    quantityDisplay = formatCount(quantityValue, unit);
                }
            }

            if (quantityDisplay == null) {
                quantityDisplay = parts.originalText();
            }

            copy.put("displayQuantity", quantityDisplay);
            copy.put("displayUnitPrice", formatAmount(displayUnitPrice));
            copy.put("displayTotalPrice", formatAmount(totalPrice));

            normalized.add(Collections.unmodifiableMap(copy));
        }
        return Collections.unmodifiableList(normalized);
    }

    private QuantityParts parseQuantity(Object raw) {
        if (raw == null) {
            return new QuantityParts(null, null, null);
        }
        String text = raw.toString().replace('\u00A0', ' ').trim();
        if (text.isEmpty()) {
            return new QuantityParts(null, null, "");
        }
        Matcher matcher = QUANTITY_PATTERN.matcher(text);
        if (matcher.find()) {
            BigDecimal value = parseDecimalString(matcher.group(1));
            String unit = matcher.group(2) != null ? matcher.group(2).trim() : null;
            return new QuantityParts(value, unit, text);
        }
        return new QuantityParts(null, null, text);
    }

    private boolean isWeightUnit(String unit) {
        return unit != null && unit.equalsIgnoreCase("kg");
    }

    private BigDecimal deriveWeight(BigDecimal unitPrice, BigDecimal totalPrice) {
        if (unitPrice == null || totalPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return totalPrice.divide(unitPrice, 4, RoundingMode.HALF_UP);
    }

    private boolean hasPriceMismatch(BigDecimal quantity, BigDecimal unitPrice, BigDecimal totalPrice) {
        if (quantity == null || unitPrice == null || totalPrice == null) {
            return false;
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) == 0) {
            return totalPrice.compareTo(BigDecimal.ZERO) != 0;
        }
        BigDecimal expectedTotal = unitPrice.multiply(quantity);
        BigDecimal difference = expectedTotal.subtract(totalPrice).abs();
        return difference.compareTo(PRICE_TOLERANCE) > 0;
    }

    private boolean isStarItem(Object name) {
        if (name == null) {
            return false;
        }
        String text = name.toString().trim();
        return text.startsWith("*");
    }

    private BigDecimal recalculateUnitPrice(BigDecimal totalPrice, BigDecimal quantity) {
        if (totalPrice == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return totalPrice.divide(quantity, 2, RoundingMode.HALF_UP);
    }

    private String formatCount(BigDecimal quantity, String unit) {
        if (quantity == null) {
            return null;
        }
        BigDecimal rounded = quantity.setScale(0, RoundingMode.HALF_UP);
        String suffix = unit != null ? " " + unit : "";
        return rounded.stripTrailingZeros().toPlainString() + suffix;
    }

    private String formatWeight(BigDecimal weight) {
        if (weight == null) {
            return null;
        }
        BigDecimal normalized = weight.max(BigDecimal.ZERO);
        BigDecimal scaled = normalized.setScale(3, RoundingMode.HALF_UP);
        return scaled.toPlainString() + " kg";
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String string) {
            return parseDecimalString(string);
        }
        return null;
    }

    private BigDecimal parseDecimalString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\u00A0', ' ').trim();
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

    private BigDecimal numericValueFromGeneral(String key) {
        Object value = general.get(key);
        return parseBigDecimal(value);
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record QuantityParts(BigDecimal value, String unit, String originalText) { }

    private static Map<String, Object> copyOfMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static List<Map<String, Object>> copyOfMapList(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> copy = new ArrayList<>(source.size());
        for (Map<String, Object> element : source) {
            if (element == null || element.isEmpty()) {
                copy.add(Map.of());
            } else {
                copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(element)));
            }
        }
        return Collections.unmodifiableList(copy);
    }
}
