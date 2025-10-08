package dev.pekelund.responsiveauth.firestore;

import dev.pekelund.responsiveauth.storage.ReceiptOwner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
