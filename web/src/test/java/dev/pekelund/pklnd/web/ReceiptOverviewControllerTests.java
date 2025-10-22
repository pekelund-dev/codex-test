package dev.pekelund.pklnd.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.web.ReceiptOwnerResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReceiptController.class)
class ReceiptOverviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptStorageService receiptStorageService;

    @MockBean
    private ReceiptExtractionService receiptExtractionService;

    @MockBean
    private ReceiptOwnerResolver receiptOwnerResolver;

    @MockBean
    private ReceiptProcessingClient receiptProcessingClient;

    @Test
    void overviewDataReturnsItemsForSelectedWeek() throws Exception {
        when(receiptStorageService.isEnabled()).thenReturn(false);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        ReceiptOwner owner = new ReceiptOwner("user-1", "Test", "test@example.com");
        when(receiptOwnerResolver.resolve(any())).thenReturn(owner);

        List<ParsedReceipt> receipts = List.of(
            createReceipt(
                "receipt-1",
                owner,
                "2024-05-18",
                "Butik B",
                List.of(createItem("Bröd", "7355550002222", new BigDecimal("25.00"), "1"))
            ),
            createReceipt(
                "receipt-2",
                owner,
                "2024-05-14",
                "Butik A",
                List.of(createItem("Mjölk", "7312340001111", new BigDecimal("12.50"), "1"))
            ),
            createReceipt(
                "receipt-3",
                owner,
                "2024-05-06",
                "Butik C",
                List.of(createItem("Smör", "7311110003333", new BigDecimal("48.00"), "1"))
            )
        );
        when(receiptExtractionService.listReceiptsForOwner(owner)).thenReturn(receipts);

        mockMvc.perform(get("/receipts/overview/data")
                .param("periodType", "week")
                .param("primary", "2024-W20")
                .with(user("alice").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parsedReceiptsEnabled").value(true))
            .andExpect(jsonPath("$.primary.totalItems").value(2))
            .andExpect(jsonPath("$.primary.items[0].name").value("Bröd"))
            .andExpect(jsonPath("$.primary.items[1].name").value("Mjölk"))
            .andExpect(jsonPath("$.primary.groups[0].ean").value("7355550002222"));
    }

    @Test
    void overviewDataSupportsMonthlyComparison() throws Exception {
        when(receiptStorageService.isEnabled()).thenReturn(false);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        ReceiptOwner owner = new ReceiptOwner("user-42", "Anna", "anna@example.com");
        when(receiptOwnerResolver.resolve(any())).thenReturn(owner);

        List<ParsedReceipt> receipts = List.of(
            createReceipt(
                "may-receipt",
                owner,
                "2024-05-08",
                "Matboden",
                List.of(
                    createItem("Ägg", "7300000000001", new BigDecimal("36.90"), "12"),
                    createItem("Mjölk", "7312340001111", new BigDecimal("14.90"), "2")
                )
            ),
            createReceipt(
                "april-receipt",
                owner,
                "2024-04-18",
                "Handla",
                List.of(createItem("Pasta", "7355550002222", new BigDecimal("24.50"), "3"))
            ),
            createReceipt(
                "march-receipt",
                owner,
                "2024-03-04",
                "Citybutiken",
                List.of(createItem("Glass", "7399990004444", new BigDecimal("48.00"), "1"))
            )
        );
        when(receiptExtractionService.listReceiptsForOwner(owner)).thenReturn(receipts);

        mockMvc.perform(get("/receipts/overview/data")
                .param("periodType", "month")
                .param("primary", "2024-05")
                .param("compare", "2024-04")
                .with(user("anna").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.primary.month").value(5))
            .andExpect(jsonPath("$.primary.year").value(2024))
            .andExpect(jsonPath("$.primary.totalItems").value(2))
            .andExpect(jsonPath("$.primary.groups[0].ean").value("7300000000001"))
            .andExpect(jsonPath("$.primary.groups[0].itemCount").value(1))
            .andExpect(jsonPath("$.comparison.month").value(4))
            .andExpect(jsonPath("$.comparison.year").value(2024))
            .andExpect(jsonPath("$.comparison.totalItems").value(1))
            .andExpect(jsonPath("$.comparison.groups[0].ean").value("7355550002222"));
    }

    private ParsedReceipt createReceipt(
        String id,
        ReceiptOwner owner,
        String receiptDate,
        String storeName,
        List<Map<String, Object>> items
    ) {
        Map<String, Object> general = Map.of(
            "storeName", storeName,
            "receiptDate", receiptDate,
            "totalAmount", "100.00"
        );
        return new ParsedReceipt(
            id,
            null,
            null,
            null,
            owner,
            "COMPLETED",
            null,
            Instant.parse(receiptDate + "T10:00:00Z"),
            general,
            items,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null
        );
    }

    private Map<String, Object> createItem(String name, String ean, BigDecimal totalPrice, String quantity) {
        return Map.of(
            "name", name,
            "ean", ean,
            "totalPrice", totalPrice,
            "displayTotalPrice", totalPrice.toPlainString(),
            "quantity", quantity,
            "displayQuantity", quantity
        );
    }
}
