package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.config.SecurityConfig;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({DemoController.class, ReceiptUploadController.class})
@ContextConfiguration(classes = PknldApplication.class)
@Import({SecurityConfig.class, ViteManifest.class, FirestoreReadTotals.class, DemoSessionService.class, ReceiptScopeHelper.class})
class DemoModeTests {

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

    @MockitoBean
    private GrantedAuthoritiesMapper oauthAuthoritiesMapper;

    @Test
    void demoEndpoint_ShouldRedirectToUploads() throws Exception {
        mockMvc.perform(get("/demo"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/receipts/uploads"));
    }

    @Test
    void uploadsPage_WithDemoSession_ShouldRenderReceiptUploadsView() throws Exception {
        when(receiptStorageService.isEnabled()).thenReturn(false);
        when(receiptExtractionService.isEnabled()).thenReturn(false);
        when(receiptOwnerResolver.resolve(any()))
            .thenReturn(new ReceiptOwner("demo-test-id", "Demo", null));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(DemoSessionService.DEMO_USER_ID_ATTRIBUTE, "demo-test-id");

        mockMvc.perform(get("/receipts/uploads").session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("receipt-uploads"));
    }
}
