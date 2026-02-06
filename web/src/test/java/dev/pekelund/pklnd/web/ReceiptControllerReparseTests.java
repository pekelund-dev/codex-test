package dev.pekelund.pklnd.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptFile;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.web.assets.ViteManifest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReceiptController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import(ViteManifest.class)
class ReceiptControllerReparseTests {

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

    @MockitoBean
    private DashboardStatisticsService dashboardStatisticsService;

    @MockitoBean
    private dev.pekelund.pklnd.firestore.CategoryService categoryService;

    @MockitoBean
    private dev.pekelund.pklnd.firestore.TagService tagService;

    @MockitoBean
    private dev.pekelund.pklnd.firestore.ItemCategorizationService itemCategorizationService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void reparseReceiptPreparesAndTriggersWhenFileExists() throws Exception {
        ParsedReceipt receipt = sampleReceipt();
        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.findById("receipt-1")).thenReturn(Optional.of(receipt));
        when(receiptStorageService.isEnabled()).thenReturn(true);
        when(receiptStorageService.listReceipts()).thenReturn(List.of(
            new ReceiptFile("uploads/r1.pdf", 200, Instant.now(), "application/pdf", receipt.owner())));
        when(receiptOwnerResolver.resolve(any())).thenReturn(receipt.owner());

        mockMvc.perform(post("/receipts/receipt-1/reparse").param("scope", "all").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/receipts/receipt-1?scope=all"))
            .andExpect(flash().attribute("successMessage", "Receipt re-parsing started."));

        verify(receiptExtractionService).prepareReceiptForReparse(receipt);
        verify(receiptProcessingClient).reparseReceipt("bucket", "uploads/r1.pdf", receipt.owner());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void reparseReceiptDoesNotExecuteWhenFileIsMissing() throws Exception {
        ParsedReceipt receipt = sampleReceipt();
        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.findById("receipt-1")).thenReturn(Optional.of(receipt));
        when(receiptStorageService.isEnabled()).thenReturn(true);
        when(receiptStorageService.listReceipts()).thenReturn(List.of());

        mockMvc.perform(post("/receipts/receipt-1/reparse").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/receipts/receipt-1"))
            .andExpect(flash().attribute("errorMessage", "Receipt file could not be found."));

        verify(receiptExtractionService, never()).prepareReceiptForReparse(any());
        verify(receiptProcessingClient, never()).reparseReceipt(any(), any(), any());
    }

    private ParsedReceipt sampleReceipt() {
        ReceiptOwner owner = new ReceiptOwner("owner-1", "Owner", "owner@example.com");
        return new ParsedReceipt(
            "receipt-1",
            "bucket",
            "uploads/r1.pdf",
            "gs://bucket/uploads/r1.pdf",
            owner,
            "FAILED",
            "Failed",
            Instant.now(),
            Map.of("fileName", "r1.pdf"),
            List.of(),
            ParsedReceipt.ReceiptItemHistory.empty(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            "boom",
            null
        );
    }
}
