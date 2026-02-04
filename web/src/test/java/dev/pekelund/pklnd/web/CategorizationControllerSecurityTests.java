package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.PknldApplication;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategorizationController.class)
@ContextConfiguration(classes = PknldApplication.class)
@Import(ViteManifest.class)
class CategorizationControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private dev.pekelund.pklnd.firestore.CategoryService categoryService;

    @MockitoBean
    private dev.pekelund.pklnd.firestore.TagService tagService;

    @MockitoBean
    private ItemCategorizationService itemCategorizationService;

    @MockitoBean
    private ReceiptExtractionService receiptExtractionService;

    @MockitoBean
    private ReceiptOwnerResolver receiptOwnerResolver;

    @MockitoBean
    private FirestoreReadTotals firestoreReadTotals;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void removeTagFromItem_RejectsWhenReceiptIsOwnedBySomeoneElse() throws Exception {
        when(itemCategorizationService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptOwnerResolver.resolve(any()))
            .thenReturn(new ReceiptOwner("owner-1", "Owner One", "owner1@example.com"));
        when(receiptExtractionService.findById("receipt-123"))
            .thenReturn(Optional.of(receiptForOwner(new ReceiptOwner("owner-2", "Owner Two", "owner2@example.com"))));

        mockMvc.perform(delete("/api/categorization/receipts/{receiptId}/items/tags/{tagId}", "receipt-123", "tag-1")
                .with(csrf())
                .param("itemIdentifier", "item-1"))
            .andExpect(status().isForbidden());

        verify(itemCategorizationService, never())
            .removeTagFromItem(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void removeTagFromItem_AllowsWhenReceiptOwnedByCurrentUser() throws Exception {
        when(itemCategorizationService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptOwnerResolver.resolve(any()))
            .thenReturn(new ReceiptOwner("owner-1", "Owner One", "owner1@example.com"));
        when(receiptExtractionService.findById("receipt-123"))
            .thenReturn(Optional.of(receiptForOwner(new ReceiptOwner("owner-1", "Owner One", "owner1@example.com"))));

        mockMvc.perform(delete("/api/categorization/receipts/{receiptId}/items/tags/{tagId}", "receipt-123", "tag-1")
                .with(csrf())
                .param("itemIdentifier", "item-1"))
            .andExpect(status().isNoContent());

        verify(itemCategorizationService)
            .removeTagFromItem(eq("receipt-123"), eq("item-1"), eq("tag-1"), eq("owner-1"));
    }

    private ParsedReceipt receiptForOwner(ReceiptOwner owner) {
        return new ParsedReceipt(
            "receipt-123",
            null,
            null,
            null,
            owner,
            "processed",
            null,
            Instant.now(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null
        );
    }
}
