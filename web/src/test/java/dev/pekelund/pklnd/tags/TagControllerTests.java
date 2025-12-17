package dev.pekelund.pklnd.tags;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pekelund.pklnd.config.ReceiptOwnerResolver;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
}
