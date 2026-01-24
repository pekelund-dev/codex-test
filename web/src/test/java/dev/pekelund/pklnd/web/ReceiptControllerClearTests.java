package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.web.assets.ViteManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceiptController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import(ViteManifest.class)
class ReceiptControllerClearTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReceiptOwnerResolver receiptOwnerResolver;

    @MockBean
    private ReceiptStorageService receiptStorageService;

    @MockBean
    private ReceiptExtractionService receiptExtractionService;

    @MockBean
    private DashboardStatisticsService dashboardStatisticsService;

    @MockBean
    private FirestoreReadTotals firestoreReadTotals;

    @Test
    @WithMockUser(username = "testuser")
    void clearReceipts_shouldCallDeleteOnServices_whenEnabled() throws Exception {
        // Arrange
        ReceiptOwner owner = new ReceiptOwner("testuser", null, null);
        when(receiptOwnerResolver.resolve(any())).thenReturn(owner);
        when(receiptStorageService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        // Act
        mockMvc.perform(post("/receipts/clear")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/receipts"));

        // Assert
        verify(receiptStorageService).deleteReceiptsForOwner(owner);
        verify(receiptExtractionService).deleteReceiptsForOwner(owner);
    }
}
