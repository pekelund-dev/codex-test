package dev.pekelund.pklnd.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReceiptController.class)
@Import(FirestoreReadTotals.class)
class ReceiptOverviewPageTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptStorageService receiptStorageService;

    @MockitoBean
    private ReceiptExtractionService receiptExtractionService;

    @MockitoBean
    private ReceiptOwnerResolver receiptOwnerResolver;

    @Test
    void receiptOverviewPageIncludesSelectionScript() throws Exception {
        when(receiptStorageService.isEnabled()).thenReturn(false);
        when(receiptExtractionService.isEnabled()).thenReturn(false);
        when(receiptOwnerResolver.resolve(any())).thenReturn(new ReceiptOwner("user-123", "Test User", "test@example.com"));

        mockMvc.perform(get("/receipts/overview").with(user("alice").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-overview-root")))
            .andExpect(content().string(containsString("/js/receipt-overview.js")));
    }
}
