package dev.pekelund.pklnd.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService.ReceiptItemReference;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.time.Instant;
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
class ReceiptControllerItemViewTests {

    @Mock
    private ReceiptExtractionService receiptExtractionService;

    @Mock
    private ReceiptOwnerResolver receiptOwnerResolver;

    private ReceiptController controller;
    private Authentication authentication;
    private ReceiptOwner owner;

    @BeforeEach
    void setUp() {
        controller = new ReceiptController(null, receiptExtractionService, receiptOwnerResolver, null);
        authentication = new TestingAuthenticationToken("user", "password", "ROLE_USER");
        owner = new ReceiptOwner("owner-1", "Test User", "user@example.com");

        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptOwnerResolver.resolve(authentication)).thenReturn(owner);
    }

    @Test
    void viewItemPurchasesUsesItemIndexWithoutBatchReceiptReads() {
        String ean = "7310867001823";
        ReceiptItemReference reference = new ReceiptItemReference(
            "receipt-1",
            owner.id(),
            Instant.parse("2024-10-01T10:15:30Z"),
            "2024-09-30",
            "ICA Kvantum",
            "ICA Kvantum",
            "receipt-1.pdf",
            Map.of(
                "name", "Mjölk",
                "eanCode", ean,
                "totalPrice", "15.90"
            )
        );

        when(receiptExtractionService.findReceiptItemReferences(eq(ean), eq(owner), eq(false)))
            .thenReturn(List.of(reference));

        Model model = new ExtendedModelMap();
        String viewName = controller.viewItemPurchases(ean, reference.receiptId(), "my", model, authentication);

        assertThat(viewName).isEqualTo("receipt-item");
        assertThat(model.getAttribute("purchaseCount")).isEqualTo(1);
        assertThat(model.getAttribute("itemName")).isEqualTo("Mjölk");
        assertThat(model.getAttribute("itemEan")).isEqualTo(ean);
        verify(receiptExtractionService, never()).findByIds(anyCollection());
        verify(receiptExtractionService, never()).findById(anyString());
    }

    @Test
    void viewItemPurchasesFallsBackToSourceReceiptWhenIndexMissing() {
        String ean = "7310867001823";

        when(receiptExtractionService.findReceiptItemReferences(eq(ean), eq(owner), eq(false)))
            .thenReturn(List.of());

        Map<String, Object> general = Map.of(
            "storeName", "ICA Maxi",
            "receiptDate", "2024-09-15",
            "fileName", "receipt-2.pdf"
        );
        Map<String, Object> item = Map.of(
            "name", "Mjölk",
            "eanCode", ean,
            "totalPrice", "17.50"
        );

        ParsedReceipt sourceReceipt = new ParsedReceipt(
            "receipt-2",
            "bucket",
            "receipt-2.pdf",
            "gs://bucket/receipt-2.pdf",
            owner,
            "COMPLETED",
            null,
            Instant.parse("2024-10-02T11:00:00Z"),
            general,
            List.of(item),
            ParsedReceipt.ReceiptItemHistory.empty(),
            List.of(),
            List.of(),
            List.of(),
            "",
            "",
            null
        );

        when(receiptExtractionService.findById("receipt-2")).thenReturn(Optional.of(sourceReceipt));

        Model model = new ExtendedModelMap();
        String viewName = controller.viewItemPurchases(ean, "receipt-2", "my", model, authentication);

        assertThat(viewName).isEqualTo("receipt-item");
        assertThat(model.getAttribute("purchaseCount")).isEqualTo(1);
        assertThat(model.getAttribute("sourceReceiptId")).isEqualTo("receipt-2");
        assertThat(model.getAttribute("itemName")).isEqualTo("Mjölk");
        verify(receiptExtractionService, never()).findByIds(anyCollection());
        verify(receiptExtractionService).findById("receipt-2");
    }
}

