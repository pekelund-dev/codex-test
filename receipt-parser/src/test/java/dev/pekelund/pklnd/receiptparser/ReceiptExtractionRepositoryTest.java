package dev.pekelund.pklnd.receiptparser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReceiptExtractionRepositoryTest {

    @Test
    void normalizeItemsSupportsMapValues() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "Mjölk");
        item.put("eanCode", "7310865004703");

        Map<String, Object> itemsMap = new LinkedHashMap<>();
        itemsMap.put("0", item);

        List<Map<String, Object>> normalized = ReceiptExtractionRepository.normalizeItems(itemsMap);

        assertThat(normalized).hasSize(1);
        Map<String, Object> first = normalized.get(0);
        assertThat(first)
            .containsEntry("name", "Mjölk")
            .containsEntry("eanCode", "7310865004703");
    }

    @Test
    void extractNormalizedEanReadsEanCodeField() {
        Map<String, Object> item = Map.of("eanCode", "7310865004703");

        String normalized = ReceiptExtractionRepository.extractNormalizedEan(item);

        assertThat(normalized).isEqualTo("7310865004703");
    }

    @Test
    void resolveItemsFallsBackToLegacySectionWhenPrimaryLacksEans() {
        Map<String, Object> legacyItem = new LinkedHashMap<>();
        legacyItem.put("name", "Mjölk");
        legacyItem.put("eanCode", "7310865004703");

        Map<String, Object> legacySection = Map.of("items", List.of(legacyItem));

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("items", List.of(Map.of("name", "Mjölk")));
        structured.put("legacy", legacySection);

        List<Map<String, Object>> resolved = ReceiptExtractionRepository.resolveItems(structured);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0))
            .containsEntry("name", "Mjölk")
            .containsEntry("eanCode", "7310865004703");
    }

    @Test
    void resolveGeneralBackfillsMissingFieldsFromLegacySection() {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("general", Map.of("receiptDate", "2024-10-01"));
        structured.put("legacy", Map.of("general", Map.of("storeName", "ICA Test", "receiptDate", "2024-09-30")));

        Map<String, Object> resolved = ReceiptExtractionRepository.resolveGeneral(structured);

        assertThat(resolved)
            .containsEntry("storeName", "ICA Test")
            .containsEntry("receiptDate", "2024-10-01");
    }
}
