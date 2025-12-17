package dev.pekelund.pklnd.tags;

import dev.pekelund.pklnd.config.ReceiptOwnerResolver;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TagController {

    private final TagService tagService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;

    public TagController(
        TagService tagService,
        Optional<ReceiptExtractionService> receiptExtractionService,
        ReceiptOwnerResolver receiptOwnerResolver
    ) {
        this.tagService = tagService;
        this.receiptExtractionService = receiptExtractionService;
        this.receiptOwnerResolver = receiptOwnerResolver;
    }

    @GetMapping("/tags")
    public String listTags(Model model, Authentication authentication, Locale locale) {
        String ownerId = resolveOwnerId(authentication);
        List<TagView> tagOptions = tagService.listTagOptions(ownerId, locale);
        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            model.addAttribute("pageTitle", "Taggar");
            model.addAttribute("tags", List.of());
            model.addAttribute("hasReceipts", false);
            model.addAttribute("tagOptions", tagOptions);
            return "tags";
        }

        boolean canViewAll = authentication != null
            && authentication.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN"));
        ReceiptOwner owner = canViewAll ? null : receiptOwnerResolver.resolve(authentication);
        List<ParsedReceipt> receipts = canViewAll
            ? receiptExtractionService.get().listAllReceipts()
            : receiptExtractionService.get().listReceiptsForOwner(owner);

        Map<String, Set<TaggedItem>> tagItems = buildTagItems(receipts, locale, ownerId);
        List<TagSummary> summaries = new ArrayList<>();
        Map<String, TagView> tagOptionsById = tagOptions
            .stream()
            .collect(Collectors.toMap(TagView::id, option -> option));

        for (Map.Entry<String, Set<TaggedItem>> entry : tagItems.entrySet()) {
            String tagId = entry.getKey();
            TagView view = tagOptionsById.get(tagId);
            if (view == null) {
                continue;
            }
            List<TaggedItem> items = entry.getValue().stream()
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
            summaries.add(new TagSummary(view, items));
        }

        summaries.sort((a, b) -> a.tag().name().compareToIgnoreCase(b.tag().name()));

        model.addAttribute("pageTitle", "Taggar");
        model.addAttribute("tags", summaries);
        model.addAttribute("hasReceipts", !receipts.isEmpty());
        model.addAttribute("tagOptions", tagOptions);
        return "tags";
    }

    @PostMapping("/tags/assign")
    public String assignTag(
        @RequestParam("ean") String ean,
        @RequestParam(value = "existingTagId", required = false) String existingTagId,
        @RequestParam(value = "nameSv", required = false) String nameSv,
        @RequestParam(value = "nameEn", required = false) String nameEn,
        @RequestParam(value = "redirect", required = false) String redirect,
        Authentication authentication,
        Locale locale,
        RedirectAttributes redirectAttributes
    ) {
        String ownerId = resolveOwnerId(authentication);
        if (!StringUtils.hasText(ownerId)) {
            redirectAttributes.addFlashAttribute("tagError", "Kunde inte spara tagg utan användare.");
            return buildRedirect(redirect);
        }
        if (!StringUtils.hasText(ean)) {
            redirectAttributes.addFlashAttribute("tagError", "Kunde inte spara tagg utan EAN-kod.");
            return buildRedirect(redirect);
        }
        Map<String, String> translations = new LinkedHashMap<>();
        if (StringUtils.hasText(nameSv)) {
            translations.put("sv", nameSv);
        }
        if (StringUtils.hasText(nameEn)) {
            translations.put("en", nameEn);
        }
        if (translations.isEmpty() && !StringUtils.hasText(existingTagId)) {
            redirectAttributes.addFlashAttribute("tagError", "Välj en befintlig tagg eller ange ett namn.");
            return buildRedirect(redirect);
        }

        String tagId = StringUtils.hasText(existingTagId) ? existingTagId : null;
        tagService.assignTagToEan(ownerId, ean, tagId, translations);
        redirectAttributes.addFlashAttribute("tagSuccess", "Tagg sparad.");
        return buildRedirect(redirect);
    }

    private Map<String, Set<TaggedItem>> buildTagItems(List<ParsedReceipt> receipts, Locale locale, String ownerId) {
        Map<String, Set<TaggedItem>> grouped = new LinkedHashMap<>();
        if (receipts == null || receipts.isEmpty()) {
            return grouped;
        }
        Map<String, Set<String>> mappings = tagService.tagMappings(ownerId);
        if (mappings.isEmpty()) {
            return grouped;
        }

        for (ParsedReceipt receipt : receipts) {
            if (receipt == null || receipt.displayItems() == null) {
                continue;
            }
            for (Map<String, Object> item : receipt.displayItems()) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                String ean = extractEan(item);
                if (!StringUtils.hasText(ean)) {
                    continue;
                }
                Set<String> tagIds = mappings.getOrDefault(ean, Set.of());
                if (tagIds.isEmpty()) {
                    continue;
                }
                TaggedItem taggedItem = new TaggedItem(
                    resolveName(item),
                    receipt.displayName(),
                    ean
                );
                for (String tagId : tagIds) {
                    grouped.computeIfAbsent(tagId, key -> new LinkedHashSet<>()).add(taggedItem);
                }
            }
        }
        return grouped;
    }

    private String resolveName(Map<String, Object> item) {
        Object raw = item.get("name");
        if (raw == null) {
            return "Okänt föremål";
        }
        return raw.toString();
    }

    private String extractEan(Map<String, Object> item) {
        Object raw = item.get("normalizedEan");
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String buildRedirect(String redirect) {
        if (StringUtils.hasText(redirect)) {
            return "redirect:" + redirect;
        }
        return "redirect:/tags";
    }

    private String resolveOwnerId(Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        return owner != null ? owner.id() : null;
    }

    public record TagSummary(TagView tag, List<TaggedItem> items) {
    }

    public record TaggedItem(String name, String receiptName, String ean) {
    }
}
