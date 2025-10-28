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
}
