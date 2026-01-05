package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReceiptController.class)
@ContextConfiguration(classes = PknldApplication.class)
class ReceiptControllerSearchTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptStorageService receiptStorageService;

    @MockitoBean
    private ReceiptExtractionService receiptExtractionService;

    @MockitoBean
    private ReceiptOwnerResolver receiptOwnerResolver;

    @MockitoBean
    private dev.pekelund.pklnd.receipts.ReceiptProcessingClient receiptProcessingClient;

    @MockitoBean
    private FirestoreReadTotals firestoreReadTotals;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void searchReceipts_ShouldReturnEmptyResults_WhenNoQuery() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user", "User", "user@example.com");
        when(receiptOwnerResolver.resolve(any())).thenReturn(owner);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/receipts/search"))
                .andExpect(status().isOk())
                .andExpect(view().name("receipt-search"))
                .andExpect(model().attribute("searchPerformed", false))
                .andExpect(model().attribute("searchQuery", ""))
                .andExpect(model().attribute("parsedReceiptsEnabled", true));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void searchReceipts_ShouldFindMatchingReceipts_WhenQueryMatches() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user", "User", "user@example.com");
        when(receiptOwnerResolver.resolve(any())).thenReturn(owner);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        ParsedReceipt receipt1 = new ParsedReceipt(
                "receipt1",
                "bucket",
                "receipt1.pdf",
                "receipts/receipt1.pdf",
                owner,
                "completed",
                null,
                Instant.now(),
                Map.of("storeName", "ICA"),
                List.of(Map.of("name", "Mellanmjölk")),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null
        );

        ParsedReceipt receipt2 = new ParsedReceipt(
                "receipt2",
                "bucket",
                "receipt2.pdf",
                "receipts/receipt2.pdf",
                owner,
                "completed",
                null,
                Instant.now(),
                Map.of("storeName", "ICA"),
                List.of(Map.of("name", "Eko mellanmjölk")),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null
        );

        when(receiptExtractionService.searchByItemName(eq("mjölk"), eq(owner), anyBoolean()))
                .thenReturn(List.of(receipt1, receipt2));

        // Act & Assert
        mockMvc.perform(get("/receipts/search").param("q", "mjölk"))
                .andExpect(status().isOk())
                .andExpect(view().name("receipt-search"))
                .andExpect(model().attribute("searchPerformed", true))
                .andExpect(model().attribute("searchQuery", "mjölk"))
                .andExpect(model().attributeExists("searchResults"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void searchReceipts_ShouldReturnEmpty_WhenNoMatches() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user", "User", "user@example.com");
        when(receiptOwnerResolver.resolve(any())).thenReturn(owner);
        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.searchByItemName(eq("nonexistent"), eq(owner), anyBoolean()))
                .thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/receipts/search").param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(view().name("receipt-search"))
                .andExpect(model().attribute("searchPerformed", true))
                .andExpect(model().attribute("searchQuery", "nonexistent"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void searchReceipts_ShouldShowWarning_WhenFirestoreDisabled() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user", "User", "user@example.com");
        when(receiptOwnerResolver.resolve(any())).thenReturn(owner);
        when(receiptExtractionService.isEnabled()).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/receipts/search").param("q", "mjölk"))
                .andExpect(status().isOk())
                .andExpect(view().name("receipt-search"))
                .andExpect(model().attribute("parsedReceiptsEnabled", false))
                .andExpect(model().attribute("searchPerformed", false));
    }
}
