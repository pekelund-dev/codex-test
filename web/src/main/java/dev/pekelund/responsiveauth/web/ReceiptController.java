package dev.pekelund.responsiveauth.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.responsiveauth.firestore.FirestoreUserDetails;
import dev.pekelund.responsiveauth.firestore.ParsedReceipt;
import dev.pekelund.responsiveauth.firestore.ReceiptExtractionAccessException;
import dev.pekelund.responsiveauth.firestore.ReceiptExtractionService;
import dev.pekelund.responsiveauth.storage.ReceiptFile;
import dev.pekelund.responsiveauth.storage.ReceiptOwner;
import dev.pekelund.responsiveauth.storage.ReceiptOwnerMatcher;
import dev.pekelund.responsiveauth.storage.ReceiptStorageException;
import dev.pekelund.responsiveauth.storage.ReceiptStorageService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReceiptController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_UPLOAD_FILES = 50;
    private static final Pattern EAN_PATTERN = Pattern.compile("(\\d{8,14})");
    private static final Pattern QUANTITY_VALUE_PATTERN = Pattern.compile("([-+]?\\d+(?:[.,]\\d+)?)");
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

    private enum ReceiptViewScope {
        MY,
        ALL
    }

    private final Optional<ReceiptStorageService> receiptStorageService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;

    public ReceiptController(
        @Autowired(required = false) ReceiptStorageService receiptStorageService,
        @Autowired(required = false) ReceiptExtractionService receiptExtractionService
    ) {
        this.receiptStorageService = Optional.ofNullable(receiptStorageService);
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
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

    @GetMapping("/receipts/uploads")
    public String receiptUploads(
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        ReceiptViewScope scope = resolveScope(scopeParam, authentication);
        ReceiptPageData pageData = loadReceiptPageData(authentication, scope);
        boolean canViewAll = isAdmin(authentication);

        model.addAttribute("pageTitle", "Upload receipts");
        model.addAttribute("storageEnabled", pageData.storageEnabled());
        model.addAttribute("maxUploadFiles", MAX_UPLOAD_FILES);
        model.addAttribute("files", pageData.files());
        model.addAttribute("listingError", pageData.listingError());
        model.addAttribute("fileStatuses", pageData.fileStatuses());
        model.addAttribute("scopeParam", toScopeParameter(scope));
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("viewingAll", pageData.viewingAll());
        return "receipt-uploads";
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

        ReceiptOwner currentOwner = resolveReceiptOwner(authentication);
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

    private record ReceiptUploadResponse(String successMessage, String errorMessage) {
    }

    private record UploadOutcome(String successMessage, String errorMessage) {
    }

    private record ItemPurchaseView(
        String itemDisplayName,
        String itemEanCode,
        String receiptId,
        String receiptDisplayName,
        String storeName,
        String dateLabel,
        String priceLabel,
        Instant sortInstant,
        String chartDate,
        BigDecimal priceValue
    ) {
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
        boolean canViewAll = isAdmin(authentication);
        boolean viewingAll = isViewingAll(scope, authentication);

        ReceiptOwner currentOwner = resolveReceiptOwner(authentication);
        if (currentOwner == null && !viewingAll) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found.");
        }

        List<ParsedReceipt> receipts = viewingAll
            ? receiptExtractionService.get().listAllReceipts()
            : receiptExtractionService.get().listReceiptsForOwner(currentOwner);
        ParsedReceipt receipt = receipts.stream()
            .filter(parsed -> documentId.equals(parsed.id()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found."));

        boolean ownsReceipt = currentOwner != null && ReceiptOwnerMatcher.belongsToCurrentOwner(receipt.owner(), currentOwner);

        Map<String, Long> itemOccurrences = computeItemOccurrencesByEan(receipts);
        List<Map<String, Object>> receiptItems = prepareReceiptItems(receipt.displayItems(), itemOccurrences);

        String displayName = receipt.displayName();
        model.addAttribute("pageTitle", displayName != null ? "Receipt: " + displayName : "Receipt details");
        model.addAttribute("receipt", receipt);
        model.addAttribute("itemOccurrences", itemOccurrences);
        model.addAttribute("receiptItems", receiptItems);
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("scopeParam", toScopeParameter(scope));
        model.addAttribute("viewingAll", viewingAll);
        model.addAttribute("ownsReceipt", ownsReceipt);
        return "receipt-detail";
    }

    @GetMapping("/receipts/items/{eanCode}")
    public String viewItemPurchases(
        @PathVariable("eanCode") String eanCode,
        @RequestParam(value = "sourceId", required = false) String sourceReceiptId,
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed receipts are not available.");
        }

        ReceiptViewScope scope = resolveScope(scopeParam, authentication);
        boolean canViewAll = isAdmin(authentication);
        boolean viewingAll = isViewingAll(scope, authentication);

        ReceiptOwner currentOwner = resolveReceiptOwner(authentication);
        if ((!viewingAll && currentOwner == null) || !StringUtils.hasText(eanCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
        }

        String trimmedEanCode = eanCode.trim();
        List<ParsedReceipt> receipts = viewingAll
            ? receiptExtractionService.get().listAllReceipts()
            : receiptExtractionService.get().listReceiptsForOwner(currentOwner);
        List<ItemPurchaseView> purchases = buildItemPurchases(trimmedEanCode, receipts);

        if (purchases.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
        }

        String displayItemName = purchases.stream()
            .map(ItemPurchaseView::itemDisplayName)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse("EAN " + trimmedEanCode);
        String displayEanCode = purchases.get(0).itemEanCode();
        List<Map<String, Object>> priceHistory = buildPriceHistory(purchases);
        String priceHistoryJson = serializePriceHistory(priceHistory);
        boolean hasPriceHistory = !priceHistory.isEmpty();

        ParsedReceipt sourceReceipt = null;
        if (StringUtils.hasText(sourceReceiptId)) {
            sourceReceipt = receipts.stream()
                .filter(parsed -> sourceReceiptId.equals(parsed.id()))
                .findFirst()
                .orElse(null);
        }

        model.addAttribute("pageTitle", "Item: " + displayItemName);
        model.addAttribute("itemName", displayItemName);
        model.addAttribute("itemEan", displayEanCode);
        model.addAttribute("purchases", purchases);
        model.addAttribute("purchaseCount", purchases.size());
        model.addAttribute("priceHistoryJson", priceHistoryJson);
        model.addAttribute("hasPriceHistory", hasPriceHistory);
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("scopeParam", toScopeParameter(scope));
        model.addAttribute("viewingAll", viewingAll);

        model.addAttribute("sourceReceiptId", sourceReceipt != null ? sourceReceipt.id() : null);
        model.addAttribute("sourceReceiptName", sourceReceipt != null ? resolveReceiptDisplayName(sourceReceipt) : null);

        return "receipt-item";
    }

    private Map<String, Long> computeItemOccurrencesByEan(List<ParsedReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> occurrences = new HashMap<>();
        for (ParsedReceipt receipt : receipts) {
            if (receipt == null) {
                continue;
            }
            for (Map<String, Object> item : receipt.displayItems()) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                String ean = extractItemEan(item);
                if (ean == null) {
                    continue;
                }
                occurrences.merge(ean, 1L, Long::sum);
            }
        }
        return Collections.unmodifiableMap(occurrences);
    }

    private List<ItemPurchaseView> buildItemPurchases(String targetEan, List<ParsedReceipt> receipts) {
        if (!StringUtils.hasText(targetEan) || receipts == null || receipts.isEmpty()) {
            return List.of();
        }

        String normalizedTarget = targetEan.trim();
        List<ItemPurchaseView> purchases = new ArrayList<>();

        for (ParsedReceipt receipt : receipts) {
            if (receipt == null) {
                continue;
            }

            String receiptDisplayName = resolveReceiptDisplayName(receipt);
            LocalDate parsedReceiptDate = parseReceiptDate(receipt.receiptDate());
            Instant sortInstant = determineSortInstant(parsedReceiptDate, receipt.updatedAt());
            String dateLabel = determineDateLabel(receipt.receiptDate(), receipt.updatedAt());
            String chartDate = parsedReceiptDate != null
                ? parsedReceiptDate.toString()
                : sortInstant != null
                    ? sortInstant.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    : null;

            for (Map<String, Object> item : receipt.displayItems()) {
                if (item == null || item.isEmpty()) {
                    continue;
                }

                String itemEan = extractItemEan(item);
                if (itemEan == null || !itemEan.equals(normalizedTarget)) {
                    continue;
                }

                String itemName = extractDisplayName(item.get("name"));

                BigDecimal totalPrice = resolveTotalPrice(item);
                BigDecimal unitPrice = resolveUnitPrice(item, totalPrice);
                String priceLabel = determinePriceLabel(item, unitPrice, totalPrice);
                BigDecimal resolvedPrice = unitPrice != null ? unitPrice : totalPrice;
                BigDecimal priceValue = resolvedPrice != null ? resolvedPrice.setScale(2, RoundingMode.HALF_UP) : null;

                purchases.add(new ItemPurchaseView(
                    itemName,
                    itemEan,
                    receipt.id(),
                    receiptDisplayName,
                    receipt.storeName(),
                    dateLabel,
                    priceLabel,
                    sortInstant,
                    chartDate,
                    priceValue
                ));
            }
        }

        purchases.sort(Comparator.comparing(ItemPurchaseView::sortInstant, Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(purchases);
    }

    private List<Map<String, Object>> buildPriceHistory(List<ItemPurchaseView> purchases) {
        if (purchases == null || purchases.isEmpty()) {
            return List.of();
        }

        return purchases.stream()
            .filter(entry -> entry.chartDate() != null && entry.priceValue() != null)
            .sorted(Comparator.comparing(ItemPurchaseView::sortInstant, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(entry -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("date", entry.chartDate());
                point.put("price", entry.priceValue());
                return point;
            })
            .toList();
    }

    private BigDecimal resolveTotalPrice(Map<String, Object> item) {
        BigDecimal totalPrice = parseBigDecimal(item.get("totalPrice"));
        if (totalPrice == null) {
            totalPrice = parseBigDecimal(item.get("displayTotalPrice"));
        }
        return totalPrice;
    }

    private BigDecimal resolveUnitPrice(Map<String, Object> item, BigDecimal totalPrice) {
        BigDecimal unitPrice = parseBigDecimal(item.get("unitPrice"));
        if (unitPrice == null) {
            unitPrice = parseBigDecimal(item.get("displayUnitPrice"));
        }
        if (unitPrice != null) {
            return unitPrice;
        }

        BigDecimal effectiveTotal = totalPrice != null ? totalPrice : resolveTotalPrice(item);
        if (effectiveTotal == null) {
            return null;
        }

        BigDecimal quantity = parseQuantityValue(item.get("quantity"));
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            quantity = parseQuantityValue(item.get("displayQuantity"));
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        try {
            return effectiveTotal.divide(quantity, 2, RoundingMode.HALF_UP);
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private BigDecimal parseQuantityValue(Object rawQuantity) {
        if (rawQuantity == null) {
            return null;
        }
        String text = rawQuantity.toString().replace('\u00A0', ' ').trim();
        if (text.isEmpty()) {
            return null;
        }

        Matcher matcher = QUANTITY_VALUE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String numeric = matcher.group(1).replace(" ", "").replace(',', '.');
        try {
            return new BigDecimal(numeric);
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private String serializePriceHistory(List<Map<String, Object>> priceHistory) {
        if (priceHistory == null || priceHistory.isEmpty()) {
            return "[]";
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(priceHistory);
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Failed to serialize price history for item chart", ex);
            return "[]";
        }
    }

    private String resolveReceiptDisplayName(ParsedReceipt receipt) {
        if (receipt == null) {
            return null;
        }
        String displayName = receipt.displayName();
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        if (StringUtils.hasText(receipt.objectPath())) {
            return receipt.objectPath();
        }
        if (StringUtils.hasText(receipt.objectName())) {
            return receipt.objectName();
        }
        return receipt.id();
    }

    private LocalDate parseReceiptDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Instant determineSortInstant(LocalDate receiptDate, Instant updatedAt) {
        if (receiptDate != null) {
            return receiptDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        return updatedAt;
    }

    private String determineDateLabel(String rawReceiptDate, Instant updatedAt) {
        if (StringUtils.hasText(rawReceiptDate)) {
            return rawReceiptDate;
        }
        return formatInstant(updatedAt);
    }

    private String determinePriceLabel(Map<String, Object> item, BigDecimal unitPrice, BigDecimal totalPrice) {
        Object displayUnit = item.get("displayUnitPrice");
        if (displayUnit != null) {
            return displayUnit.toString();
        }
        if (unitPrice != null) {
            return formatAmount(unitPrice);
        }
        Object rawUnit = item.get("unitPrice");
        if (rawUnit != null) {
            return rawUnit.toString();
        }

        Object displayTotal = item.get("displayTotalPrice");
        if (displayTotal != null) {
            return displayTotal.toString();
        }
        if (totalPrice != null) {
            return formatAmount(totalPrice);
        }
        Object rawTotal = item.get("totalPrice");
        return rawTotal != null ? rawTotal.toString() : null;
    }

    private String extractDisplayName(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.replace('\u00A0', ' ').trim();
            if (normalized.isEmpty()) {
                return null;
            }
            normalized = normalized.replace(" ", "");
            normalized = normalized.replace(',', '.');
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private UploadOutcome processUpload(List<MultipartFile> files, Authentication authentication) {
        ReceiptStorageService storage = receiptStorageService
            .filter(ReceiptStorageService::isEnabled)
            .orElse(null);

        if (storage == null) {
            return new UploadOutcome(null,
                "Receipt uploads are disabled. Configure Google Cloud Storage to enable this feature.");
        }

        List<MultipartFile> sanitizedFiles = files == null ? List.of()
            : files.stream().filter(file -> file != null && !file.isEmpty()).toList();

        if (sanitizedFiles.isEmpty()) {
            return new UploadOutcome(null, "Please choose at least one file to upload.");
        }

        if (sanitizedFiles.size() > MAX_UPLOAD_FILES) {
            return new UploadOutcome(null,
                "You can upload up to %d files at a time.".formatted(MAX_UPLOAD_FILES));
        }

        ReceiptOwner owner = resolveReceiptOwner(authentication);

        try {
            storage.uploadFiles(sanitizedFiles, owner);
            int count = sanitizedFiles.size();
            String successMessage = "%d file%s uploaded successfully.".formatted(count, count == 1 ? "" : "s");
            return new UploadOutcome(successMessage, null);
        } catch (ReceiptStorageException ex) {
            LOGGER.error("Failed to upload receipts", ex);
            return new UploadOutcome(null, ex.getMessage());
        }
    }

    @PostMapping("/receipts/upload")
    public String uploadReceipts(
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        RedirectAttributes redirectAttributes,
        Authentication authentication
    ) {
        UploadOutcome outcome = processUpload(files, authentication);
        if (outcome.successMessage() != null) {
            redirectAttributes.addFlashAttribute("successMessage", outcome.successMessage());
        }
        if (outcome.errorMessage() != null) {
            redirectAttributes.addFlashAttribute("errorMessage", outcome.errorMessage());
        }
        return "redirect:/receipts/uploads";
    }

    @PostMapping(value = "/receipts/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ReceiptUploadResponse> uploadReceiptsJson(
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        Authentication authentication
    ) {
        UploadOutcome outcome = processUpload(files, authentication);
        HttpStatus status = outcome.errorMessage() != null && outcome.successMessage() == null
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.OK;

        return ResponseEntity.status(status)
            .body(new ReceiptUploadResponse(outcome.successMessage(), outcome.errorMessage()));
    }

    @PostMapping("/receipts/clear")
    public String clearReceipts(RedirectAttributes redirectAttributes, Authentication authentication) {
        ReceiptOwner owner = resolveReceiptOwner(authentication);
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
        ReceiptOwner owner = resolveReceiptOwner(authentication);
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

    private ReceiptOwner resolveReceiptOwner(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        String identifier = authentication.getName();
        String displayName = null;
        String email = null;
        Object principal = authentication.getPrincipal();

        if (principal instanceof FirestoreUserDetails firestoreUserDetails) {
            identifier = firestoreUserDetails.getId();
            displayName = firestoreUserDetails.getDisplayName();
            email = firestoreUserDetails.getUsername();
        } else if (principal instanceof OAuth2User oAuth2User) {
            displayName = readAttribute(oAuth2User, "name");
            email = readAttribute(oAuth2User, "email");
            String subject = readAttribute(oAuth2User, "sub");
            identifier = StringUtils.hasText(subject) ? subject : oAuth2User.getName();
            if (!StringUtils.hasText(displayName)) {
                displayName = StringUtils.hasText(email) ? email : authentication.getName();
            }
        } else if (principal instanceof UserDetails userDetails) {
            identifier = userDetails.getUsername();
            displayName = userDetails.getUsername();
            email = userDetails.getUsername();
        } else if (principal instanceof String stringPrincipal) {
            displayName = stringPrincipal;
        }

        if (!StringUtils.hasText(identifier)) {
            identifier = authentication.getName();
        }

        ReceiptOwner owner = new ReceiptOwner(identifier, displayName, email);
        return owner.hasValues() ? owner : null;
    }

    private String readAttribute(OAuth2User oAuth2User, String attributeName) {
        Object value = oAuth2User.getAttributes().get(attributeName);
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return stringValue;
        }
        return null;
    }

}

