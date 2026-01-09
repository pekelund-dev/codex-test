package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceiptController.class)
@ContextConfiguration(classes = PknldApplication.class)
class ReceiptControllerLocalTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptStorageService receiptStorageService;

    @MockitoBean
    private ReceiptExtractionService receiptExtractionService;

    @MockitoBean
    private ReceiptOwnerResolver receiptOwnerResolver;

    @MockitoBean
    private DashboardStatisticsService dashboardStatisticsService;

    @MockitoBean
    private dev.pekelund.pklnd.receipts.ReceiptProcessingClient receiptProcessingClient;

    @MockitoBean
    private FirestoreReadTotals firestoreReadTotals;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void clearReceipts_ShouldSucceed_WhenStorageDisabledAndFirestoreEnabled() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("user", "User", "user@example.com");
        when(receiptOwnerResolver.resolve(any())).thenReturn(owner);
        
        // Simulate local env: Storage disabled, Firestore enabled
        when(receiptStorageService.isEnabled()).thenReturn(false);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        // Act
        mockMvc.perform(post("/receipts/clear").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receipts"))
                .andExpect(flash().attributeExists("successMessage"))
                .andExpect(flash().attribute("successMessage", "Cleared parsed receipt data."));

        // Assert
        verify(receiptStorageService, never()).deleteReceiptsForOwner(any());
        verify(receiptExtractionService).deleteReceiptsForOwner(owner);
    }
}
