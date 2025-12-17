package dev.pekelund.pklnd.tags;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pekelund.pklnd.config.ReceiptOwnerResolver;
import dev.pekelund.pklnd.firestore.ReceiptExtractionAccessException;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@ExtendWith(MockitoExtension.class)
class TagControllerTests {

    private MockMvc mockMvc;

    @Mock
    private TagService tagService;

    @Mock
    private ReceiptExtractionService receiptExtractionService;

    @Mock
    private ReceiptOwnerResolver receiptOwnerResolver;

    @BeforeEach
    void setUp() {
        TagController controller = new TagController(tagService, Optional.of(receiptExtractionService), receiptOwnerResolver);
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(viewResolver).build();
    }

    @Test
    void returnsUserFriendlyErrorWhenEanIsMissing() throws Exception {
        when(receiptOwnerResolver.resolve(any())).thenReturn(new ReceiptOwner("alice", "Alice", "alice@example.com"));

        mockMvc
            .perform(post("/tags/assign").param("ean", " ").param("redirect", "/receipts/1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/receipts/1"))
            .andExpect(flash().attribute("tagError", "Kunde inte spara tagg utan EAN-kod."));

        verifyNoInteractions(tagService);
    }

    @Test
    void showsErrorWhenAssigningFails() throws Exception {
        when(receiptOwnerResolver.resolve(any())).thenReturn(new ReceiptOwner("alice", "Alice", "alice@example.com"));
        when(tagService.assignTagToEan(any(), any(), any(), any()))
            .thenThrow(new TagAccessException("boom", new RuntimeException("firestore")));

        mockMvc
            .perform(
                post("/tags/assign")
                    .param("ean", "7312345678")
                    .param("existingTagId", "tag-1")
                    .param("nameSv", "Grönsak")
                    .param("redirect", "/receipts/1")
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/receipts/1"))
            .andExpect(flash().attribute("tagError", "Kunde inte spara tagg just nu. Försök igen."));
    }

    @Test
    void rendersTagErrorWhenListFails() throws Exception {
        when(tagService.listTagOptions(any(), any())).thenThrow(new TagAccessException("boom", new RuntimeException("firestore")));

        mockMvc
            .perform(get("/tags"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("tagError", "Kunde inte läsa taggar just nu. Försök igen senare."));
    }

    @Test
    void showsErrorWhenReceiptsFailToLoad() throws Exception {
        when(tagService.listTagOptions(any(), any())).thenReturn(List.of());
        when(receiptOwnerResolver.resolve(any())).thenReturn(new ReceiptOwner("alice", "Alice", "alice@example.com"));
        when(receiptExtractionService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.listReceiptsForOwner(any()))
            .thenThrow(new ReceiptExtractionAccessException("firestore down"));

        mockMvc
            .perform(get("/tags"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("tagError", "Kunde inte läsa kvitton just nu. Försök igen senare."))
            .andExpect(model().attribute("hasReceipts", false));
    }
}
