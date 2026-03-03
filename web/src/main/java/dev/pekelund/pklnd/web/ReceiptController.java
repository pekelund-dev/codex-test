package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionAccessException;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient;
import dev.pekelund.pklnd.storage.ReceiptFile;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptOwnerMatcher;
import dev.pekelund.pklnd.storage.ReceiptStorageException;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.web.receipts.ReceiptViewScope;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReceiptController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Pattern EAN_PATTERN = Pattern.compile("(\\d{8,14})");
    private static final List<String> POSSIBLE_EAN_KEYS = List.of(
        "eanCode",
        "ean",
        "barcode",
        "barCode",
        "ean_code",
        "EAN",
        "gtin",
        "itemEan",
        "sku"
    );
    private static final String SCOPE_MY = "my";
    private static final String SCOPE_ALL = "all";

    private final Optional<ReceiptStorageService> receiptStorageService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;
    private final Optional<ReceiptProcessingClient> receiptProcessingClient;
    private final DashboardStatisticsService dashboardStatisticsService;
    private final Optional<dev.pekelund.pklnd.firestore.CategoryService> categoryService;
    private final Optional<dev.pekelund.pklnd.firestore.TagService> tagService;
    private final Optional<dev.pekelund.pklnd.firestore.ItemCategorizationService> itemCategorizationService;

    public ReceiptController(
        @Autowired(required = false) ReceiptStorageService receiptStorageService,
        @Autowired(required = false) ReceiptExtractionService receiptExtractionService,
        ReceiptOwnerResolver receiptOwnerResolver,
        @Autowired(required = false) ReceiptProcessingClient receiptProcessingClient,
        DashboardStatisticsService dashboardStatisticsService,
        @Autowired(required = false) dev.pekelund.pklnd.firestore.CategoryService categoryService,
        @Autowired(required = false) dev.pekelund.pklnd.firestore.TagService tagService,
        @Autowired(required = false) dev.pekelund.pklnd.firestore.ItemCategorizationService itemCategorizationService
    ) {
        this.receiptStorageService = Optional.ofNullable(receiptStorageService);
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
        this.receiptOwnerResolver = receiptOwnerResolver;
        this.receiptProcessingClient = Optional.ofNullable(receiptProcessingClient);
        this.dashboardStatisticsService = dashboardStatisticsService;
        this.categoryService = Optional.ofNullable(categoryService);
        this.tagService = Optional.ofNullable(tagService);
        this.itemCategorizationService = Optional.ofNullable(itemCategorizationService);
    }

    @GetMapping("/receipts")
    public String receipts(
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        ReceiptViewScope scope = resolveScope(scopeParam, authentication);
        ReceiptPageData pageData = loadReceiptPageData(authentication, scope);
        boolean canViewAll = isAdmin(authentication);

        model.addAttribute("pageTitle", "Receipts");
        model.addAttribute("storageEnabled", pageData.storageEnabled());
        model.addAttribute("files", pageData.files());
        model.addAttribute("listingError", pageData.listingError());
        model.addAttribute("parsedReceiptsEnabled", pageData.parsedReceiptsEnabled());
        model.addAttribute("parsedReceipts", pageData.parsedReceipts());
        model.addAttribute("parsedListingError", pageData.parsedListingError());
        model.addAttribute("fileStatuses", pageData.fileStatuses());
        model.addAttribute("scopeParam", toScopeParameter(scope));
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("viewingAll", pageData.viewingAll());
        return "receipts";
    }

    @GetMapping("/receipts/errors")
    public String receiptErrors(Model model, Authentication authentication) {
        model.addAttribute("pageTitle", "Failed Parsing");
        model.addAttribute("canReparse", isAdmin(authentication));
        List<ParsedReceipt> failedReceipts = dashboardStatisticsService.getFailedReceipts(authentication);
        model.addAttribute("failedReceipts", failedReceipts);
        return "receipt-errors";
    }

    @PostMapping("/receipts/{documentId}/reparse")
    public String reparseReceipt(@PathVariable("documentId") String documentId,
                                 @RequestParam(value = "scope", required = false) String scopeParam,
                                 RedirectAttributes redirectAttributes,
                                 Authentication authentication) {
        String redirectTarget = StringUtils.hasText(scopeParam)
            ? "/receipts/" + documentId + "?scope=" + scopeParam
            : "/receipts/" + documentId;

        if (!isAdmin(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only admins can re-parse receipts.");
            return "redirect:" + redirectTarget;
        }

        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Receipt service unavailable.");
            return "redirect:" + redirectTarget;
        }

        ParsedReceipt receipt = receiptExtractionService.get().findById(documentId).orElse(null);
        if (receipt == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Receipt not found.");
            return "redirect:/receipts/errors";
        }

        if (!receiptFileExists(receipt)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Receipt file could not be found.");
            return "redirect:" + redirectTarget;
        }

        if (receiptProcessingClient.isPresent()) {
            try {
                if (StringUtils.hasText(receipt.bucket()) && StringUtils.hasText(receipt.objectName())) {
                    receiptExtractionService.get().prepareReceiptForReparse(receipt);
                    receiptProcessingClient.get().reparseReceipt(receipt.bucket(), receipt.objectName(), receipt.owner());
                    redirectAttributes.addFlashAttribute("successMessage", "Receipt re-parsing started.");
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", "Receipt missing storage location info.");
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to trigger reparse", ex);
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to trigger re-parsing: " + ex.getMessage());
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Processing client unavailable.");
        }

        return "redirect:" + redirectTarget;
    }

    @GetMapping(value = "/receipts/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ReceiptDashboardResponse receiptsDashboard(
        @RequestParam(value = "scope", required = false) String scopeParam,
        Authentication authentication
    ) {
        ReceiptViewScope scope = resolveScope(scopeParam, authentication);
        ReceiptPageData pageData = loadReceiptPageData(authentication, scope);
        boolean canViewAll = isAdmin(authentication);

        List<ReceiptFileEntry> fileEntries = pageData.files().stream()
            .map(file -> toReceiptFileEntry(file, pageData.fileStatuses()))
            .toList();

        List<ParsedReceiptEntry> parsedEntries = pageData.parsedReceipts().stream()
            .map(this::toParsedReceiptEntry)
            .toList();

        return new ReceiptDashboardResponse(
            pageData.storageEnabled(),
            pageData.listingError(),
            fileEntries,
            pageData.parsedReceiptsEnabled(),
            pageData.parsedListingError(),
            parsedEntries,
            pageData.viewingAll(),
            toScopeParameter(scope),
            canViewAll
        );
    }

    private ReceiptPageData loadReceiptPageData(Authentication authentication, ReceiptViewScope scope) {
        boolean storageEnabled = receiptStorageService.isPresent() && receiptStorageService.get().isEnabled();
        List<ReceiptFile> receiptFiles = List.of();
        String listingError = null;
        boolean viewingAll = isViewingAll(scope, authentication);

        if (storageEnabled) {
            try {
                receiptFiles = receiptStorageService.get().listReceipts();
            } catch (ReceiptStorageException ex) {
                listingError = ex.getMessage();
                LOGGER.warn("Failed to list receipt files", ex);
            }
        }

        boolean parsedReceiptsEnabled = receiptExtractionService.isPresent() && receiptExtractionService.get().isEnabled();
        List<ParsedReceipt> parsedReceipts = List.of();
        String parsedListingError = null;

        ReceiptOwner currentOwner = receiptOwnerResolver.resolve(authentication);
        if (currentOwner == null && !viewingAll) {
            receiptFiles = List.of();
        } else {
            if (!viewingAll) {
                receiptFiles = receiptFiles.stream()
                    .filter(file -> ReceiptOwnerMatcher.belongsToCurrentOwner(file.owner(), currentOwner))
                    .toList();
            }

            if (parsedReceiptsEnabled) {
                try {
                    parsedReceipts = viewingAll
                        ? receiptExtractionService.get().listAllReceipts()
                        : receiptExtractionService.get().listReceiptsForOwner(currentOwner);
                } catch (ReceiptExtractionAccessException ex) {
                    parsedListingError = ex.getMessage();
                    LOGGER.warn("Failed to list parsed receipts", ex);
                }
            }
        }

        Map<String, ParsedReceipt> fileStatuses = parsedReceipts.stream()
            .filter(parsed -> parsed != null && StringUtils.hasText(parsed.objectName()))
            .collect(Collectors.toMap(
                ParsedReceipt::objectName,
                Function.identity(),
                (existing, replacement) -> replacement,
                LinkedHashMap::new
            ));

        return new ReceiptPageData(
            storageEnabled,
            receiptFiles,
            listingError,
            parsedReceiptsEnabled,
            parsedReceipts,
            parsedListingError,
            fileStatuses,
            viewingAll
        );
    }

    private ReceiptFileEntry toReceiptFileEntry(ReceiptFile file, Map<String, ParsedReceipt> fileStatuses) {
        ParsedReceipt status = fileStatuses.getOrDefault(file.name(), null);
        String updated = formatInstant(file.updated());

        String statusBadgeClass = status != null ? status.statusBadgeClass() : "bg-secondary-subtle text-secondary";
        String statusValue = status != null ? status.status() : null;
        String statusMessage = status != null ? status.statusMessage() : null;

        return new ReceiptFileEntry(
            file.name(),
            file.displayName(),
            file.name(),
            file.formattedSize(),
            file.ownerDisplayName(),
            updated,
            file.contentType(),
            statusValue,
            statusMessage,
            statusBadgeClass
        );
    }

    private ParsedReceiptEntry toParsedReceiptEntry(ParsedReceipt parsed) {
        String displayName = parsed.displayName() != null ? parsed.displayName() : parsed.objectPath();
        String updatedAt = formatInstant(parsed.updatedAt());
        String detailsUrl = parsed.id() != null ? "/receipts/" + parsed.id() : null;

        return new ParsedReceiptEntry(
            parsed.id(),
            displayName,
            parsed.objectPath(),
            parsed.objectName(),
            parsed.storeName(),
            parsed.receiptDate(),
            parsed.totalAmount(),
            parsed.formattedTotalAmount(),
            updatedAt,
            parsed.reconciliationStatus(),
            parsed.status(),
            parsed.statusMessage(),
            parsed.statusBadgeClass(),
            detailsUrl
        );
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return TIMESTAMP_FORMATTER.format(instant);
    }

    private record ReceiptPageData(
        boolean storageEnabled,
        List<ReceiptFile> files,
        String listingError,
        boolean parsedReceiptsEnabled,
        List<ParsedReceipt> parsedReceipts,
        String parsedListingError,
        Map<String, ParsedReceipt> fileStatuses,
        boolean viewingAll
    ) {
    }

    private record ReceiptFileEntry(
        String objectName,
        String displayName,
        String name,
        String formattedSize,
        String ownerDisplayName,
        String updated,
        String contentType,
        String status,
        String statusMessage,
        String statusBadgeClass
    ) {
    }

    private record ParsedReceiptEntry(
        String id,
        String displayName,
        String objectPath,
        String objectName,
        String storeName,
        String receiptDate,
        String totalAmount,
        String formattedTotalAmount,
        String updatedAt,
        String reconciliationStatus,
        String status,
        String statusMessage,
        String statusBadgeClass,
        String detailsUrl
    ) {
    }

    private record ReceiptDashboardResponse(
        boolean storageEnabled,
        String listingError,
        List<ReceiptFileEntry> files,
        boolean parsedReceiptsEnabled,
        String parsedListingError,
        List<ParsedReceiptEntry> parsedReceipts,
        boolean viewingAll,
        String scope,
        boolean canViewAll
    ) {
    }

    private record ReceiptClearResponse(String successMessage, String errorMessage) {
    }

    private record ClearOutcome(String successMessage, String errorMessage) {
    }

    @GetMapping("/receipts/{documentId}")
    public String viewParsedReceipt(
        @PathVariable("documentId") String documentId,
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed receipts are not available.");
        }

        ReceiptViewScope scope = resolveScope(scopeParam, authentication);
        ReceiptViewScope effectiveScope = scope;
        boolean canViewAll = isAdmin(authentication);

        ParsedReceipt receipt = receiptExtractionService
            .get()
            .findById(documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found."));

        ReceiptOwner currentOwner = receiptOwnerResolver.resolve(authentication);
        boolean ownsReceipt =
            currentOwner != null && ReceiptOwnerMatcher.belongsToCurrentOwner(receipt.owner(), currentOwner);
        if (!ownsReceipt && !canViewAll) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found.");
        }

        boolean viewingAll = isViewingAll(scope, authentication) || (canViewAll && !ownsReceipt);
        if (viewingAll) {
            effectiveScope = ReceiptViewScope.ALL;
        }

        ReceiptOwner statsOwner = viewingAll ? null : receipt.owner();
        Set<String> normalizedEans = receipt.displayItems().stream()
            .map(this::extractItemEan)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Long> itemOccurrences = resolveItemOccurrences(receipt, normalizedEans, statsOwner, viewingAll);
        List<Map<String, Object>> receiptItems = prepareReceiptItems(receipt.displayItems(), itemOccurrences);

        // Load categories and tags data
        if (categoryService.isPresent() && categoryService.get().isEnabled()) {
            model.addAttribute("categoriesHierarchy", categoryService.get().getCategoriesHierarchy());
            model.addAttribute("categories", categoryService.get().listCategories());
        } else {
            model.addAttribute("categoriesHierarchy", Map.of());
            model.addAttribute("categories", List.of());
        }
        
        if (tagService.isPresent() && tagService.get().isEnabled()) {
            model.addAttribute("tags", tagService.get().listTags());
        } else {
            model.addAttribute("tags", List.of());
        }
        
        // Load categorization data for this receipt
        Map<String, String> itemCategoryMap = new HashMap<>();
        Map<String, List<String>> itemTagsMap = new HashMap<>();
        
        if (itemCategorizationService.isPresent() && itemCategorizationService.get().isEnabled()) {
            List<dev.pekelund.pklnd.firestore.ItemCategoryMapping> categoryMappings = 
                itemCategorizationService.get().getCategoriesForReceipt(documentId);
            List<dev.pekelund.pklnd.firestore.ItemTagMapping> tagMappings = 
                itemCategorizationService.get().getTagsForReceipt(documentId);
            
            // Build map of item identifier -> category ID
            for (dev.pekelund.pklnd.firestore.ItemCategoryMapping mapping : categoryMappings) {
                String itemId = mapping.itemEan() != null ? mapping.itemEan() : mapping.itemIndex();
                itemCategoryMap.put(itemId, mapping.categoryId());
            }
            
            // Build map of item identifier -> list of tag IDs
            for (dev.pekelund.pklnd.firestore.ItemTagMapping mapping : tagMappings) {
                String itemId = mapping.itemEan() != null ? mapping.itemEan() : mapping.itemIndex();
                itemTagsMap.computeIfAbsent(itemId, k -> new ArrayList<>()).add(mapping.tagId());
            }
            
            model.addAttribute("itemCategories", categoryMappings);
            model.addAttribute("itemTags", tagMappings);
            model.addAttribute("itemCategoryMap", itemCategoryMap);
            model.addAttribute("itemTagsMap", itemTagsMap);
            model.addAttribute("categorizationEnabled", true);
        } else {
            model.addAttribute("itemCategories", List.of());
            model.addAttribute("itemTags", List.of());
            model.addAttribute("itemCategoryMap", Map.of());
            model.addAttribute("itemTagsMap", Map.of());
            model.addAttribute("categorizationEnabled", false);
        }

        String displayName = receipt.displayName();
        model.addAttribute("pageTitle", displayName != null ? "Receipt: " + displayName : "Receipt details");
        model.addAttribute("receipt", receipt);
        model.addAttribute("itemOccurrences", itemOccurrences);
        model.addAttribute("receiptItems", receiptItems);
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("scopeParam", toScopeParameter(effectiveScope));
        model.addAttribute("viewingAll", viewingAll);
        model.addAttribute("ownsReceipt", ownsReceipt);
        model.addAttribute("canReparse", canViewAll && receiptProcessingClient.isPresent()
            && receiptExtractionService.isPresent() && receiptExtractionService.get().isEnabled());
        model.addAttribute("reparseAction", "/receipts/" + receipt.id() + "/reparse");
        return "receipt-detail";
    }

    private Map<String, Long> resolveItemOccurrences(ParsedReceipt receipt, Set<String> normalizedEans,
        ReceiptOwner statsOwner, boolean viewingAll) {

        if (normalizedEans == null || normalizedEans.isEmpty()) {
            return Map.of();
        }

        ParsedReceipt.ReceiptItemHistory history = receipt.itemHistory();
        boolean useGlobal = viewingAll;
        if (history != null && history.containsAll(normalizedEans, useGlobal)) {
            Map<String, Long> occurrences = new LinkedHashMap<>();
            for (String ean : normalizedEans) {
                long count = history.countFor(ean, useGlobal);
                occurrences.put(ean, count);
            }
            return Map.copyOf(occurrences);
        }

        return receiptExtractionService.get().loadItemOccurrences(normalizedEans, statsOwner, viewingAll);
    }

    private List<Map<String, Object>> prepareReceiptItems(List<Map<String, Object>> items, Map<String, Long> occurrences) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> prepared = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            if (item == null || item.isEmpty()) {
                prepared.add(Map.of());
                continue;
            }

            String normalizedEan = extractItemEan(item);
            Map<String, Object> copy = new LinkedHashMap<>(item);
            copy.put("normalizedEan", normalizedEan);
            long historyCount = normalizedEan != null && occurrences != null
                ? occurrences.getOrDefault(normalizedEan, 0L)
                : 0L;
            copy.put("historyCount", historyCount);

            prepared.add(Collections.unmodifiableMap(copy));
        }

        return Collections.unmodifiableList(prepared);
    }

    private String extractItemEan(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }

        for (String key : POSSIBLE_EAN_KEYS) {
            Object raw = item.get(key);
            String normalized = extractEanCode(raw);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String extractEanCode(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        String text = rawValue.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        Matcher matcher = EAN_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        String digitsOnly = text.replaceAll("\\D+", "");
        if (digitsOnly.length() >= 8 && digitsOnly.length() <= 14) {
            return digitsOnly;
        }

        if (text.chars().allMatch(Character::isDigit) && text.length() >= 8 && text.length() <= 14) {
            return text;
        }

        return null;
    }

    private String extractDisplayName(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    @PostMapping("/receipts/clear")
    public String clearReceipts(RedirectAttributes redirectAttributes, Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to determine the current user.");
            return "redirect:/receipts";
        }

        ClearOutcome outcome = clearReceiptData(owner);

        if (outcome.successMessage() != null) {
            redirectAttributes.addFlashAttribute("successMessage", outcome.successMessage());
        }

        if (outcome.errorMessage() != null) {
            redirectAttributes.addFlashAttribute("errorMessage", outcome.errorMessage());
        }

        return "redirect:/receipts";
    }

    @PostMapping(value = "/receipts/clear", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ReceiptClearResponse> clearReceiptsJson(Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ReceiptClearResponse(null, "Unable to determine the current user."));
        }

        ClearOutcome outcome = clearReceiptData(owner);
        HttpStatus status = outcome.successMessage() == null && outcome.errorMessage() != null
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.OK;

        return ResponseEntity.status(status)
            .body(new ReceiptClearResponse(outcome.successMessage(), outcome.errorMessage()));
    }

    private ClearOutcome clearReceiptData(ReceiptOwner owner) {
        boolean storageEnabled = receiptStorageService.isPresent() && receiptStorageService.get().isEnabled();
        boolean parsedReceiptsEnabled = receiptExtractionService.isPresent() && receiptExtractionService.get().isEnabled();

        if (!storageEnabled && !parsedReceiptsEnabled) {
            return new ClearOutcome(null, "Receipt storage and parsing are disabled.");
        }

        List<String> successes = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (storageEnabled) {
            try {
                receiptStorageService.get().deleteReceiptsForOwner(owner);
                successes.add("uploaded receipts");
            } catch (ReceiptStorageException ex) {
                errors.add("Failed to clear uploaded receipts: " + ex.getMessage());
                LOGGER.error("Failed to clear uploaded receipts", ex);
            }
        }

        if (parsedReceiptsEnabled) {
            try {
                receiptExtractionService.get().deleteReceiptsForOwner(owner);
                successes.add("parsed receipt data");
            } catch (ReceiptExtractionAccessException ex) {
                errors.add("Failed to clear parsed receipt data: " + ex.getMessage());
                LOGGER.error("Failed to clear parsed receipts", ex);
            }
        }

        String successMessage = null;
        if (!successes.isEmpty()) {
            successMessage = "Cleared " + String.join(" and ", successes) + ".";
        }

        String errorMessage = null;
        if (!errors.isEmpty()) {
            errorMessage = String.join(" ", errors);
        }

        if (successMessage == null && errorMessage == null) {
            errorMessage = "No receipt data was cleared.";
        }

        return new ClearOutcome(successMessage, errorMessage);
    }

    private ReceiptViewScope resolveScope(String scopeParam, Authentication authentication) {
        if (scopeParam != null && SCOPE_ALL.equalsIgnoreCase(scopeParam) && isAdmin(authentication)) {
            return ReceiptViewScope.ALL;
        }
        return ReceiptViewScope.MY;
    }

    private boolean isViewingAll(ReceiptViewScope scope, Authentication authentication) {
        return scope == ReceiptViewScope.ALL && isAdmin(authentication);
    }

    private boolean receiptFileExists(ParsedReceipt receipt) {
        if (receipt == null || !StringUtils.hasText(receipt.objectName())
            || receiptStorageService.isEmpty() || !receiptStorageService.get().isEnabled()) {
            return false;
        }

        try {
            return receiptStorageService.get().fileExists(receipt.objectName());
        } catch (ReceiptStorageException ex) {
            LOGGER.warn("Unable to verify receipt file existence for {}", receipt.objectName(), ex);
            return false;
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_ADMIN");
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (authority.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private String toScopeParameter(ReceiptViewScope scope) {
        return scope == ReceiptViewScope.ALL ? SCOPE_ALL : SCOPE_MY;
    }

}
