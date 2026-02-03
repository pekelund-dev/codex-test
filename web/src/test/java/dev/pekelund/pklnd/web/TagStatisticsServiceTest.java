package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.ItemTag;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TagStatisticsServiceTest {

    @Test
    void summarizeTags_ShouldAggregateCountsAndTotals() {
        ItemCategorizationService categorizationService = mock(ItemCategorizationService.class);
        ReceiptExtractionService receiptExtractionService = mock(ReceiptExtractionService.class);

        when(categorizationService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        ParsedReceipt receipt = new ParsedReceipt(
            "receipt-1",
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            Map.of("storeName", "ICA"),
            List.of(Map.of("totalPrice", new BigDecimal("10.50"), "normalizedEan", "12345678")),
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null
        );

        when(receiptExtractionService.listAllReceipts()).thenReturn(List.of(receipt));
        when(categorizationService.getItemsByTag("tag-1")).thenReturn(List.of(
            new ItemCategorizationService.TaggedItemInfo("receipt-1", "0", null, Instant.now())
        ));

        TagStatisticsService service = new TagStatisticsService(
            java.util.Optional.of(categorizationService),
            java.util.Optional.of(receiptExtractionService)
        );

        ItemTag tag = ItemTag.builder().id("tag-1").name("Frys").build();
        Map<String, TagStatisticsService.TagSummary> summaries = service.summarizeTags(List.of(tag));

        TagStatisticsService.TagSummary summary = summaries.get("tag-1");
        assertThat(summary).isNotNull();
        assertThat(summary.itemCount()).isEqualTo(1);
        assertThat(summary.storeCount()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualByComparingTo(new BigDecimal("10.50"));
    }
}
