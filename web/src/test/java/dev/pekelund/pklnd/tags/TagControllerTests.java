package dev.pekelund.pklnd.tags;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pekelund.pklnd.config.ReceiptOwnerResolver;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.ReceiptExtractionAccessException;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.web.GitMetadata;
import dev.pekelund.pklnd.web.LanguageOption;
import dev.pekelund.pklnd.web.UserProfile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

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
        SpringResourceTemplateResolver templateResolver = new SpringResourceTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setPrefix("classpath:/templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setApplicationContext(new StaticApplicationContext());

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding("UTF-8");

        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(new TestLayoutAdvice())
            .setViewResolvers(viewResolver)
            .build();
    }

    @ControllerAdvice
    static class TestLayoutAdvice {

        @ModelAttribute("userProfile")
        UserProfile userProfile() {
            return UserProfile.anonymous();
        }

        @ModelAttribute("supportedLanguages")
        List<LanguageOption> supportedLanguages() {
            return List.of(new LanguageOption("sv", "nav.language.swedish", "/"));
        }

        @ModelAttribute("currentRequestUri")
        String currentRequestUri() {
            return "/tags";
        }

        @ModelAttribute("firestoreReadTotals")
        FirestoreReadTotals firestoreReadTotals() {
            return new FirestoreReadTotals();
        }

        @ModelAttribute("gitMetadata")
        GitMetadata gitMetadata() {
            return GitMetadata.empty();
        }

        @ModelAttribute("environmentLabel")
        String environmentLabel() {
            return null;
        }
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
            .andExpect(model().attribute("hasReceipts", false))
            .andExpect(content().string(not(containsString("Inga kvitton att visa taggar för ännu."))));
    }
}
