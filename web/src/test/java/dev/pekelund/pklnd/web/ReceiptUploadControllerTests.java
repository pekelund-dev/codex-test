package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.web.assets.ViteManifest;
import dev.pekelund.pklnd.web.receipts.ReceiptScopeHelper;
import dev.pekelund.pklnd.web.receipts.ReceiptUploadController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReceiptUploadController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import({ViteManifest.class, FirestoreReadTotals.class, ReceiptScopeHelper.class})
class ReceiptUploadControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptStorageService receiptStorageService;

    @MockitoBean
    private ReceiptExtractionService receiptExtractionService;

    @MockitoBean
    private ReceiptOwnerResolver receiptOwnerResolver;

    @MockitoBean
    private ReceiptProcessingClient receiptProcessingClient;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void receiptUploadsPage_ShouldRenderReceiptUploadsView() throws Exception {
        when(receiptStorageService.isEnabled()).thenReturn(false);
        when(receiptExtractionService.isEnabled()).thenReturn(false);
        when(receiptOwnerResolver.resolve(any()))
            .thenReturn(new ReceiptOwner("user-1", "Test User", "user@example.com"));

        mockMvc.perform(get("/receipts/uploads"))
            .andExpect(status().isOk())
            .andExpect(view().name("receipt-uploads"));
    }
}
