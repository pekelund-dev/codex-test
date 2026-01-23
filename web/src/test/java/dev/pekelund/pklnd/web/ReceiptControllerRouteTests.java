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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReceiptController.class)
@ContextConfiguration(classes = PknldApplication.class)
class ReceiptControllerRouteTests {

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
    @WithMockUser(username = "user", roles = "USER")
    void receiptsRoute_ShouldRenderReceiptsView() throws Exception {
        when(receiptStorageService.isEnabled()).thenReturn(false);
        when(receiptExtractionService.isEnabled()).thenReturn(false);
        when(receiptOwnerResolver.resolve(any()))
            .thenReturn(new ReceiptOwner("user", "User", "user@example.com"));

        mockMvc.perform(get("/receipts"))
            .andExpect(status().isOk())
            .andExpect(view().name("receipts"));
    }
}
