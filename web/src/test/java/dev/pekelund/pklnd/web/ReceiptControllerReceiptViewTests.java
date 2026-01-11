package dev.pekelund.pklnd.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class ReceiptControllerReceiptViewTests {

    @Mock
    private ReceiptExtractionService receiptExtractionService;

    @Mock
    private ReceiptOwnerResolver receiptOwnerResolver;

    private ReceiptController controller;
    private Authentication authentication;
    private ReceiptOwner owner;

    @BeforeEach
    void setUp() {
        controller = new ReceiptController(null, receiptExtractionService, receiptOwnerResolver, null, null, null, null, null);
        authentication = new TestingAuthenticationToken("user", "password", "ROLE_USER");
        owner = new ReceiptOwner("owner-1", "Test User", "user@example.com");

        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptOwnerResolver.resolve(authentication)).thenReturn(owner);
    }

    @Test
    void viewParsedReceiptUsesEmbeddedHistoryCounts() {
        Map<String, Object> general = Map.of(
            "storeName", "ICA Maxi",
            "receiptDate", "2024-09-15"
        );
        Map<String, Object> itemOne = new LinkedHashMap<>();
        itemOne.put("name", "Mjölk");
        itemOne.put("ean", "7310865004703");
        Map<String, Object> itemTwo = new LinkedHashMap<>();
        itemTwo.put("name", "Bröd");
        itemTwo.put("ean", "7310867001823");

        ParsedReceipt receipt = new ParsedReceipt(
            "receipt-1",
            "bucket",
            "receipt-1.pdf",
            "gs://bucket/receipt-1.pdf",
            owner,
            "COMPLETED",
            null,
            Instant.parse("2024-09-16T12:00:00Z"),
            general,
            List.of(itemOne, itemTwo),
            new ParsedReceipt.ReceiptItemHistory(
                Map.of("7310865004703", 3L, "7310867001823", 2L),
                Map.of("7310865004703", 5L, "7310867001823", 4L)
            ),
            List.of(),
            List.of(),
            List.of(),
            "",
            "",
            null,
            null
        );

        when(receiptExtractionService.findById("receipt-1")).thenReturn(Optional.of(receipt));

        Model model = new ExtendedModelMap();
        String viewName = controller.viewParsedReceipt("receipt-1", "my", model, authentication);

        assertThat(viewName).isEqualTo("receipt-detail");
        @SuppressWarnings("unchecked")
        Map<String, Long> occurrences = (Map<String, Long>) model.getAttribute("itemOccurrences");
        assertThat(occurrences)
            .containsEntry("7310865004703", 3L)
            .containsEntry("7310867001823", 2L);
        verify(receiptExtractionService, never()).loadItemOccurrences(any(), any(), anyBoolean());
    }

    @Test
    void viewParsedReceiptFallsBackWhenCountsMissing() {
        Map<String, Object> general = Map.of(
            "storeName", "ICA Maxi",
            "receiptDate", "2024-09-15"
        );
        Map<String, Object> itemOne = new LinkedHashMap<>();
        itemOne.put("name", "Mjölk");
        itemOne.put("ean", "7310865004703");
        Map<String, Object> itemTwo = new LinkedHashMap<>();
        itemTwo.put("name", "Bröd");
        itemTwo.put("ean", "7310867001823");

        ParsedReceipt receipt = new ParsedReceipt(
            "receipt-1",
            "bucket",
            "receipt-1.pdf",
            "gs://bucket/receipt-1.pdf",
            owner,
            "COMPLETED",
            null,
            Instant.parse("2024-09-16T12:00:00Z"),
            general,
            List.of(itemOne, itemTwo),
            new ParsedReceipt.ReceiptItemHistory(Map.of("7310865004703", 3L), Map.of()),
            List.of(),
            List.of(),
            List.of(),
            "",
            "",
            null,
            null
        );

        when(receiptExtractionService.findById("receipt-1")).thenReturn(Optional.of(receipt));
        when(receiptExtractionService.loadItemOccurrences(any(), any(), anyBoolean()))
            .thenReturn(Map.of("7310865004703", 3L, "7310867001823", 2L));

        Model model = new ExtendedModelMap();
        String viewName = controller.viewParsedReceipt("receipt-1", "my", model, authentication);

        assertThat(viewName).isEqualTo("receipt-detail");
        verify(receiptExtractionService).loadItemOccurrences(any(), eq(owner), eq(false));
    }
}
