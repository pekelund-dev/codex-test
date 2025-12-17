package dev.pekelund.pklnd.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.pklnd.config.ReceiptOwnerResolver;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionAccessException;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient.ProcessingFailure;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient.ProcessingResult;
import dev.pekelund.pklnd.storage.DuplicateReceiptException;
import dev.pekelund.pklnd.storage.ReceiptFile;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptOwnerMatcher;
import dev.pekelund.pklnd.storage.ReceiptStorageException;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.storage.StoredReceiptReference;
import dev.pekelund.pklnd.tags.TagService;
import dev.pekelund.pklnd.tags.TagView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
    private static final Pattern WEEK_IDENTIFIER_PATTERN = Pattern.compile("^(\\d{4})-W(\\d{2})$");

    private enum ReceiptViewScope {
        MY,
        ALL
    }

    private enum PeriodType {
        WEEK,
        MONTH
    }

    private final Optional<ReceiptStorageService> receiptStorageService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;
    private final Optional<ReceiptProcessingClient> receiptProcessingClient;
    private final TagService tagService;

    public ReceiptController(
        @Autowired(required = false) ReceiptStorageService receiptStorageService,
        @Autowired(required = false) ReceiptExtractionService receiptExtractionService,
        ReceiptOwnerResolver receiptOwnerResolver,
        @Autowired(required = false) ReceiptProcessingClient receiptProcessingClient,
        TagService tagService
    ) {
        this.receiptStorageService = Optional.ofNullable(receiptStorageService);
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
        this.receiptOwnerResolver = receiptOwnerResolver;
        this.receiptProcessingClient = Optional.ofNullable(receiptProcessingClient);
        this.tagService = tagService;
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

    @GetMapping("/receipts/overview")
    public String receiptOverview(
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        ReceiptViewScope scope = resolveScope(scopeParam, authentication);
        ReceiptPageData pageData = loadReceiptPageData(authentication, scope);
        boolean canViewAll = isAdmin(authentication);

        LocalDate today = LocalDate.now();

        model.addAttribute("pageTitleKey", "page.receipts.overview.title");
        model.addAttribute("scopeParam", toScopeParameter(scope));
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("viewingAll", pageData.viewingAll());
        model.addAttribute("parsedReceiptsEnabled", pageData.parsedReceiptsEnabled());
        model.addAttribute("defaultPeriodType", "week");
        model.addAttribute("defaultPrimaryWeek", formatWeekIdentifier(today));
        model.addAttribute("defaultPrimaryMonth", formatMonthIdentifier(today));
        model.addAttribute("defaultCompareWeek", "");
        model.addAttribute("defaultCompareMonth", "");
        return "receipt-overview";
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

    @GetMapping(value = "/receipts/overview/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ReceiptOverviewResponse> receiptOverviewData(
        @RequestParam(value = "periodType", required = false) String periodTypeParam,
        @RequestParam(value = "primary", required = false) String primaryIdentifier,
        @RequestParam(value = "compare", required = false) String compareIdentifier,
        @RequestParam(value = "scope", required = false) String scopeParam,
        Authentication authentication
    ) {
        ReceiptViewScope scope = resolveScope(scopeParam, authentication);
        ReceiptPageData pageData = loadReceiptPageData(authentication, scope);

        if (!pageData.parsedReceiptsEnabled()) {
            return ResponseEntity.ok(
                new ReceiptOverviewResponse(
                    false,
                    "Kvittotolkning måste vara aktiverad för att visa översikten.",
                    null,
                    null,
                    toScopeParameter(scope),
                    pageData.viewingAll(),
                    List.of()
                )
            );
        }

        PeriodType periodType = parsePeriodType(periodTypeParam);
        if (periodType == null) {
            return ResponseEntity.badRequest().body(
                new ReceiptOverviewResponse(
                    true,
                    "Ogiltig periodtyp angavs.",
                    null,
                    null,
                    toScopeParameter(scope),
                    pageData.viewingAll(),
                    List.of()
                )
            );
        }

        PeriodSelection primarySelection = parsePeriodSelection(periodType, primaryIdentifier);
        if (primarySelection == null) {
            return ResponseEntity.badRequest().body(
                new ReceiptOverviewResponse(
                    true,
                    "Ange en giltig huvudperiod.",
                    null,
                    null,
                    toScopeParameter(scope),
                    pageData.viewingAll(),
                    List.of()
                )
            );
        }

        PeriodSelection compareSelection = null;
        if (StringUtils.hasText(compareIdentifier)) {
            compareSelection = parsePeriodSelection(periodType, compareIdentifier);
            if (compareSelection == null) {
                return ResponseEntity.badRequest().body(
                    new ReceiptOverviewResponse(
                        true,
                        "Ange en giltig jämförelseperiod.",
                        null,
                        null,
                        toScopeParameter(scope),
                        pageData.viewingAll(),
                        List.of()
                    )
                );
            }
        }

        List<ParsedReceipt> receipts = pageData.parsedReceipts();
        String scopeValue = toScopeParameter(scope);

        PeriodOverview primaryOverview = buildPeriodOverview(primarySelection, receipts, scopeValue);
        PeriodOverview comparisonOverview = compareSelection != null
            ? buildPeriodOverview(compareSelection, receipts, scopeValue)
            : null;

        return ResponseEntity.ok(
            new ReceiptOverviewResponse(
                true,
                null,
                primaryOverview,
                comparisonOverview,
                scopeValue,
                pageData.viewingAll(),
                collectReceiptDates(receipts)
            )
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

    private PeriodType parsePeriodType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return null;
        }
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "week" -> PeriodType.WEEK;
            case "month" -> PeriodType.MONTH;
            default -> null;
        };
    }

    private PeriodSelection parsePeriodSelection(PeriodType type, String identifier) {
        if (type == null || !StringUtils.hasText(identifier)) {
            return null;
        }
        return switch (type) {
            case WEEK -> parseWeekSelection(identifier);
            case MONTH -> parseMonthSelection(identifier);
        };
    }

    private PeriodSelection parseWeekSelection(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }

        String normalized = identifier.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = WEEK_IDENTIFIER_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        int year;
        int week;
        try {
            year = Integer.parseInt(matcher.group(1));
            week = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ex) {
            return null;
        }

        WeekFields weekFields = WeekFields.ISO;
        try {
            LocalDate base = LocalDate.of(year, 1, 4);
            LocalDate start = base
                .with(weekFields.weekBasedYear(), year)
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(weekFields.dayOfWeek(), 1);
            LocalDate end = start.plusDays(6);
            String normalizedId = String.format("%04d-W%02d", year, week);
            return new PeriodSelection(PeriodType.WEEK, normalizedId, start, end, week, year, null, year);
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private PeriodSelection parseMonthSelection(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return null;
        }

        String normalized = identifier.trim();
        try {
            YearMonth month = YearMonth.parse(normalized);
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            return new PeriodSelection(
                PeriodType.MONTH,
                month.toString(),
                start,
                end,
                null,
                null,
                month.getMonthValue(),
                month.getYear()
            );
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private PeriodOverview buildPeriodOverview(
        PeriodSelection selection,
        List<ParsedReceipt> receipts,
        String scopeParam
    ) {
        if (selection == null) {
            return new PeriodOverview(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                List.of(),
                List.of(),
                null
            );
        }

        List<ParsedReceipt> source = receipts != null ? receipts : List.of();
        List<ItemOverviewEntry> items = new ArrayList<>();
        int counter = 0;

        for (ParsedReceipt receipt : source) {
            if (receipt == null) {
                continue;
            }

            LocalDate entryDate = resolveEntryDate(receipt);
            Instant sortInstant = determineSortInstant(parseReceiptDate(receipt.receiptDate()), receipt.updatedAt());
            if (entryDate == null && sortInstant != null) {
                entryDate = LocalDate.ofInstant(sortInstant, ZoneId.systemDefault());
            }

            if (entryDate == null) {
                continue;
            }
            if (entryDate.isBefore(selection.startDate()) || entryDate.isAfter(selection.endDate())) {
                continue;
            }

            String dateLabel = determineDateLabel(receipt.receiptDate(), receipt.updatedAt());
            String dateIso = entryDate.toString();
            Long sortTimestamp = sortInstant != null ? sortInstant.toEpochMilli() : null;
            String receiptName = resolveReceiptDisplayName(receipt);
            String receiptUrl = buildReceiptUrl(receipt.id(), scopeParam);
            String storeName = extractDisplayName(receipt.storeName());

            List<Map<String, Object>> displayItems = receipt.displayItems();
            if (displayItems == null || displayItems.isEmpty()) {
                continue;
            }

            for (Map<String, Object> item : displayItems) {
                if (item == null || item.isEmpty()) {
                    continue;
                }

                String itemName = extractDisplayName(item.get("name"));
                String ean = extractItemEan(item);
                BigDecimal totalPriceValue = resolveTotalPrice(item);
                if (totalPriceValue != null) {
                    totalPriceValue = totalPriceValue.setScale(2, RoundingMode.HALF_UP);
                }
                BigDecimal unitPriceValue = resolveUnitPrice(item, totalPriceValue);
                if (unitPriceValue != null) {
                    unitPriceValue = unitPriceValue.setScale(2, RoundingMode.HALF_UP);
                }
                BigDecimal quantityValue = parseQuantityValue(item.get("displayQuantity"));
                if (quantityValue == null) {
                    quantityValue = parseQuantityValue(item.get("quantity"));
                }
                String quantityLabel = determineQuantityLabel(item);

                String itemId = receipt.id() != null ? receipt.id() + ":" + (++counter) : "item-" + (++counter);

                items.add(new ItemOverviewEntry(
                    itemId,
                    itemName,
                    ean,
                    storeName,
                    receipt.id(),
                    receiptName,
                    receiptUrl,
                    dateLabel,
                    dateIso,
                    sortTimestamp,
                    unitPriceValue,
                    totalPriceValue,
                    quantityValue,
                    quantityLabel
                ));
            }
        }

        items.sort(Comparator.comparing(ItemOverviewEntry::sortTimestamp, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, GroupAccumulator> groupAccumulators = new LinkedHashMap<>();
        for (ItemOverviewEntry item : items) {
            if (!StringUtils.hasText(item.ean())) {
                continue;
            }
            groupAccumulators.computeIfAbsent(item.ean(), GroupAccumulator::new).accept(item);
        }

        List<GroupSummaryEntry> groupSummaries = groupAccumulators.values().stream()
            .map(GroupAccumulator::toSummary)
            .toList();

        Set<String> receiptIds = new HashSet<>();
        Set<String> storeNames = new HashSet<>();
        BigDecimal totalPrice = null;
        BigDecimal totalQuantity = null;

        for (ItemOverviewEntry item : items) {
            if (item == null) {
                continue;
            }
            if (StringUtils.hasText(item.receiptId())) {
                receiptIds.add(item.receiptId());
            }
            if (StringUtils.hasText(item.store())) {
                storeNames.add(item.store());
            }
            if (item.totalPriceValue() != null) {
                totalPrice = totalPrice == null ? item.totalPriceValue() : totalPrice.add(item.totalPriceValue());
            }
            if (item.quantityValue() != null) {
                totalQuantity = totalQuantity == null
                    ? item.quantityValue()
                    : totalQuantity.add(item.quantityValue());
            }
        }

        BigDecimal normalizedTotalPrice = totalPrice != null ? totalPrice.setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal normalizedTotalQuantity =
            totalQuantity != null ? totalQuantity.setScale(2, RoundingMode.HALF_UP) : null;

        PeriodSummary summary = new PeriodSummary(
            receiptIds.size(),
            storeNames.size(),
            normalizedTotalPrice,
            normalizedTotalQuantity
        );

        return new PeriodOverview(
            selection.type(),
            selection.identifier(),
            selection.startDate().toString(),
            selection.endDate().toString(),
            selection.weekNumber(),
            selection.weekYear(),
            selection.month(),
            selection.year(),
            items.size(),
            List.copyOf(items),
            List.copyOf(groupSummaries),
            summary
        );
    }

    private List<String> collectReceiptDates(List<ParsedReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return List.of();
        }

        Set<String> dates = new TreeSet<>();
        for (ParsedReceipt receipt : receipts) {
            if (receipt == null) {
                continue;
            }
            List<Map<String, Object>> displayItems = receipt.displayItems();
            if (displayItems == null || displayItems.isEmpty()) {
                continue;
            }
            LocalDate date = resolveEntryDate(receipt);
            if (date == null) {
                continue;
            }
            dates.add(date.toString());
        }
        return List.copyOf(dates);
    }

    private LocalDate resolveEntryDate(ParsedReceipt receipt) {
        if (receipt == null) {
            return null;
        }
        LocalDate parsed = parseReceiptDate(receipt.receiptDate());
        if (parsed != null) {
            return parsed;
        }
        Instant updatedAt = receipt.updatedAt();
        if (updatedAt == null) {
            return null;
        }
        return updatedAt.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String determineQuantityLabel(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }

        Object displayQuantity = item.get("displayQuantity");
        if (displayQuantity != null) {
            String text = displayQuantity.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }

        Object rawQuantity = item.get("quantity");
        if (rawQuantity != null) {
            String text = rawQuantity.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private String buildReceiptUrl(String receiptId, String scopeParam) {
        if (!StringUtils.hasText(receiptId)) {
            return null;
        }
        StringBuilder builder = new StringBuilder("/receipts/");
        builder.append(receiptId);
        if (StringUtils.hasText(scopeParam)) {
            builder.append("?scope=").append(scopeParam);
        }
        return builder.toString();
    }

    private String formatWeekIdentifier(LocalDate date) {
        LocalDate effective = date != null ? date : LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        int week = effective.get(weekFields.weekOfWeekBasedYear());
        int year = effective.get(weekFields.weekBasedYear());
        return String.format("%04d-W%02d", year, week);
    }

    private String formatMonthIdentifier(LocalDate date) {
        YearMonth month = date != null ? YearMonth.from(date) : YearMonth.now();
        return month.toString();
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

    private record ReceiptOverviewResponse(
        boolean parsedReceiptsEnabled,
        String errorMessage,
        PeriodOverview primary,
        PeriodOverview comparison,
        String scope,
        boolean viewingAll,
        List<String> receiptDates
    ) {
    }

    private record PeriodOverview(
        PeriodType type,
        String identifier,
        String startDate,
        String endDate,
        Integer weekNumber,
        Integer weekYear,
        Integer month,
        Integer year,
        int totalItems,
        List<ItemOverviewEntry> items,
        List<GroupSummaryEntry> groups,
        PeriodSummary summary
    ) {
    }

    private record PeriodSummary(
        int receiptCount,
        int storeCount,
        BigDecimal totalPriceValue,
        BigDecimal totalQuantityValue
    ) {
    }

    private record ItemOverviewEntry(
        String itemId,
        String name,
        String ean,
        String store,
        String receiptId,
        String receiptName,
        String receiptUrl,
        String dateLabel,
        String dateIso,
        Long sortTimestamp,
        BigDecimal unitPriceValue,
        BigDecimal totalPriceValue,
        BigDecimal quantityValue,
        String quantityLabel
    ) {
    }

    private record GroupSummaryEntry(
        String ean,
        String displayName,
        int itemCount,
        BigDecimal minUnitPriceValue,
        BigDecimal maxUnitPriceValue,
        BigDecimal minTotalPriceValue,
        BigDecimal maxTotalPriceValue,
        BigDecimal totalQuantityValue,
        int storeCount,
        String earliestDateIso,
        String latestDateIso,
        Long earliestTimestamp,
        Long latestTimestamp
    ) {
    }

    private record PeriodSelection(
        PeriodType type,
        String identifier,
        LocalDate startDate,
        LocalDate endDate,
        Integer weekNumber,
        Integer weekYear,
        Integer month,
        Integer year
    ) {
    }

    private static final class GroupAccumulator {

        private final String ean;
        private String displayName;
        private int itemCount;
        private BigDecimal minUnitPrice;
        private BigDecimal maxUnitPrice;
        private BigDecimal minTotalPrice;
        private BigDecimal maxTotalPrice;
        private BigDecimal quantityTotal;
        private final Set<String> stores = new HashSet<>();
        private Long earliestTimestamp;
        private Long latestTimestamp;
        private String earliestDateIso;
        private String latestDateIso;

        GroupAccumulator(String ean) {
            this.ean = ean;
        }

        void accept(ItemOverviewEntry item) {
            if (item == null) {
                return;
            }

            itemCount++;
            if (!StringUtils.hasText(displayName) && StringUtils.hasText(item.name())) {
                displayName = item.name();
            }

            BigDecimal unitPrice = item.unitPriceValue();
            if (unitPrice != null) {
                if (minUnitPrice == null || unitPrice.compareTo(minUnitPrice) < 0) {
                    minUnitPrice = unitPrice;
                }
                if (maxUnitPrice == null || unitPrice.compareTo(maxUnitPrice) > 0) {
                    maxUnitPrice = unitPrice;
                }
            }

            BigDecimal totalPrice = item.totalPriceValue();
            if (totalPrice != null) {
                if (minTotalPrice == null || totalPrice.compareTo(minTotalPrice) < 0) {
                    minTotalPrice = totalPrice;
                }
                if (maxTotalPrice == null || totalPrice.compareTo(maxTotalPrice) > 0) {
                    maxTotalPrice = totalPrice;
                }
            }

            if (item.quantityValue() != null) {
                if (quantityTotal == null) {
                    quantityTotal = item.quantityValue();
                } else {
                    quantityTotal = quantityTotal.add(item.quantityValue());
                }
            }

            if (StringUtils.hasText(item.store())) {
                stores.add(item.store());
            }

            if (item.sortTimestamp() != null) {
                long timestamp = item.sortTimestamp();
                if (earliestTimestamp == null || timestamp < earliestTimestamp) {
                    earliestTimestamp = timestamp;
                    earliestDateIso = item.dateIso();
                }
                if (latestTimestamp == null || timestamp > latestTimestamp) {
                    latestTimestamp = timestamp;
                    latestDateIso = item.dateIso();
                }
            } else if (item.dateIso() != null) {
                if (earliestDateIso == null) {
                    earliestDateIso = item.dateIso();
                }
                latestDateIso = item.dateIso();
            }
        }

        GroupSummaryEntry toSummary() {
            BigDecimal minUnit = minUnitPrice != null ? minUnitPrice.setScale(2, RoundingMode.HALF_UP) : null;
            BigDecimal maxUnit = maxUnitPrice != null ? maxUnitPrice.setScale(2, RoundingMode.HALF_UP) : null;
            BigDecimal minTotal = minTotalPrice != null ? minTotalPrice.setScale(2, RoundingMode.HALF_UP) : null;
            BigDecimal maxTotal = maxTotalPrice != null ? maxTotalPrice.setScale(2, RoundingMode.HALF_UP) : null;
            BigDecimal totalQuantity = quantityTotal != null ? quantityTotal : null;
            return new GroupSummaryEntry(
                ean,
                displayName,
                itemCount,
                minUnit,
                maxUnit,
                minTotal,
                maxTotal,
                totalQuantity,
                stores.size(),
                earliestDateIso,
                latestDateIso,
                earliestTimestamp,
                latestTimestamp
            );
        }
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
        Authentication authentication,
        Locale locale
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
        String ownerId = currentOwner != null ? currentOwner.id() : null;
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
        List<Map<String, Object>> receiptItems = prepareReceiptItems(receipt.displayItems(), itemOccurrences, locale, ownerId);

        String displayName = receipt.displayName();
        model.addAttribute("pageTitle", displayName != null ? "Receipt: " + displayName : "Receipt details");
        model.addAttribute("receipt", receipt);
        model.addAttribute("itemOccurrences", itemOccurrences);
        model.addAttribute("receiptItems", receiptItems);
        model.addAttribute("availableTags", tagService.listTagOptions(ownerId, locale));
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("scopeParam", toScopeParameter(effectiveScope));
        model.addAttribute("viewingAll", viewingAll);
        model.addAttribute("ownsReceipt", ownsReceipt);
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

    @GetMapping("/receipts/items/{eanCode}")
    public String viewItemPurchases(
        @PathVariable("eanCode") String eanCode,
        @RequestParam(value = "sourceId", required = false) String sourceReceiptId,
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication,
        Locale locale
    ) {
        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed receipts are not available.");
        }

        ReceiptViewScope scope = resolveScope(scopeParam, authentication);
        ReceiptViewScope effectiveScope = scope;
        boolean canViewAll = isAdmin(authentication);
        boolean viewingAll = isViewingAll(scope, authentication);

        ReceiptOwner currentOwner = receiptOwnerResolver.resolve(authentication);
        String ownerId = currentOwner != null ? currentOwner.id() : null;
        if ((!viewingAll && currentOwner == null) || !StringUtils.hasText(eanCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
        }

        String trimmedEanCode = eanCode.trim();
        List<ReceiptExtractionService.ReceiptItemReference> itemReferences = receiptExtractionService.get()
            .findReceiptItemReferences(trimmedEanCode, viewingAll ? null : currentOwner, viewingAll);

        ReceiptExtractionService.ReceiptItemReference sourceReference = null;
        if (StringUtils.hasText(sourceReceiptId)) {
            sourceReference = findReferenceByReceiptId(itemReferences, sourceReceiptId);
            if (!viewingAll && canViewAll && sourceReference == null) {
                List<ReceiptExtractionService.ReceiptItemReference> expandedReferences = receiptExtractionService
                    .get()
                    .findReceiptItemReferences(trimmedEanCode, null, true);
                ReceiptExtractionService.ReceiptItemReference expandedSourceReference = findReferenceByReceiptId(
                    expandedReferences,
                    sourceReceiptId
                );
                if (expandedSourceReference != null
                    && !belongsToCurrentOwner(expandedSourceReference.ownerId(), currentOwner)) {
                    viewingAll = true;
                    effectiveScope = ReceiptViewScope.ALL;
                    itemReferences = expandedReferences;
                    sourceReference = expandedSourceReference;
                }
            }
        }

        ParsedReceipt sourceReceipt = null;
        String sourceReceiptIdentifier = StringUtils.hasText(sourceReceiptId) ? sourceReceiptId.trim() : null;
        String sourceReceiptName = sourceReference != null
            ? resolveReceiptDisplayName(
                sourceReference.receiptDisplayName(),
                sourceReference.receiptStoreName(),
                sourceReference.receiptObjectName()
            )
            : null;

        List<ItemPurchaseView> purchases = buildItemPurchasesFromReferences(trimmedEanCode, itemReferences);

        if (StringUtils.hasText(sourceReceiptIdentifier)) {
            boolean includedInReferences = purchases.stream()
                .anyMatch(purchase -> sourceReceiptIdentifier.equals(purchase.receiptId()));
            if (!includedInReferences) {
                sourceReceipt = receiptExtractionService
                    .get()
                    .findById(sourceReceiptIdentifier)
                    .orElse(null);
                if (sourceReceipt != null) {
                    List<ItemPurchaseView> fallback = buildItemPurchasesFromReceipts(trimmedEanCode,
                        List.of(sourceReceipt));
                    if (!fallback.isEmpty()) {
                        List<ItemPurchaseView> combined = new ArrayList<>(purchases.size() + fallback.size());
                        combined.addAll(purchases);
                        combined.addAll(fallback);
                        purchases = sortPurchases(combined);
                    }
                    if (!StringUtils.hasText(sourceReceiptName)) {
                        sourceReceiptName = resolveReceiptDisplayName(sourceReceipt);
                    }
                }
            }
        }

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

        model.addAttribute("pageTitle", "Item: " + displayItemName);
        model.addAttribute("itemName", displayItemName);
        model.addAttribute("itemEan", displayEanCode);
        model.addAttribute("itemTags", tagService.tagsForEan(ownerId, displayEanCode, locale));
        model.addAttribute("purchases", purchases);
        model.addAttribute("purchaseCount", purchases.size());
        model.addAttribute("priceHistoryJson", priceHistoryJson);
        model.addAttribute("hasPriceHistory", hasPriceHistory);
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("scopeParam", toScopeParameter(effectiveScope));
        model.addAttribute("viewingAll", viewingAll);

        model.addAttribute("sourceReceiptId", sourceReceiptIdentifier);
        model.addAttribute("sourceReceiptName", sourceReceiptName);

        return "receipt-item";
    }

    private List<ItemPurchaseView> buildItemPurchasesFromReceipts(String targetEan, List<ParsedReceipt> receipts) {
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

        return sortPurchases(purchases);
    }

    private List<ItemPurchaseView> buildItemPurchasesFromReferences(String targetEan,
        List<ReceiptExtractionService.ReceiptItemReference> references) {
        if (!StringUtils.hasText(targetEan) || references == null || references.isEmpty()) {
            return List.of();
        }

        String normalizedTarget = targetEan.trim();
        List<ItemPurchaseView> purchases = new ArrayList<>();

        for (ReceiptExtractionService.ReceiptItemReference reference : references) {
            if (reference == null) {
                continue;
            }

            Map<String, Object> item = reference.itemData();
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

            LocalDate parsedReceiptDate = parseReceiptDate(reference.receiptDate());
            Instant sortInstant = determineSortInstant(parsedReceiptDate, reference.receiptUpdatedAt());
            String dateLabel = determineDateLabel(reference.receiptDate(), reference.receiptUpdatedAt());
            String chartDate = parsedReceiptDate != null
                ? parsedReceiptDate.toString()
                : sortInstant != null
                    ? sortInstant.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    : null;

            String receiptDisplayName = resolveReceiptDisplayName(
                reference.receiptDisplayName(),
                reference.receiptStoreName(),
                reference.receiptObjectName()
            );

            purchases.add(new ItemPurchaseView(
                itemName,
                itemEan,
                reference.receiptId(),
                receiptDisplayName,
                reference.receiptStoreName(),
                dateLabel,
                priceLabel,
                sortInstant,
                chartDate,
                priceValue
            ));
        }

        return sortPurchases(purchases);
    }

    private ReceiptExtractionService.ReceiptItemReference findReferenceByReceiptId(
        List<ReceiptExtractionService.ReceiptItemReference> references,
        String receiptId
    ) {
        if (references == null || references.isEmpty() || !StringUtils.hasText(receiptId)) {
            return null;
        }
        String trimmedId = receiptId.trim();
        return references.stream()
            .filter(reference -> trimmedId.equals(reference.receiptId()))
            .findFirst()
            .orElse(null);
    }

    private boolean belongsToCurrentOwner(String ownerId, ReceiptOwner owner) {
        if (!StringUtils.hasText(ownerId) || owner == null || !StringUtils.hasText(owner.id())) {
            return false;
        }
        return ownerId.equals(owner.id());
    }

    private List<ItemPurchaseView> sortPurchases(List<ItemPurchaseView> purchases) {
        if (purchases == null || purchases.isEmpty()) {
            return List.of();
        }
        List<ItemPurchaseView> sorted = new ArrayList<>(purchases);
        sorted.sort(Comparator.comparing(ItemPurchaseView::sortInstant,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(sorted);
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

    private List<Map<String, Object>> prepareReceiptItems(List<Map<String, Object>> items, Map<String, Long> occurrences,
        Locale locale, String ownerId) {
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
            List<TagView> tags = tagService.tagsForEan(ownerId, normalizedEan, locale);
            copy.put("tags", tags);

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
        String resolved = resolveReceiptDisplayName(receipt.displayName(), receipt.storeName(), receipt.objectName());
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        if (StringUtils.hasText(receipt.objectPath())) {
            return receipt.objectPath();
        }
        return receipt.id();
    }

    private String resolveReceiptDisplayName(String displayName, String storeName, String objectName) {
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        if (StringUtils.hasText(storeName)) {
            return storeName;
        }
        if (StringUtils.hasText(objectName)) {
            return objectName;
        }
        return null;
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
                "Uppladdning av kvitton är inaktiverad. Konfigurera Google Cloud Storage för att aktivera funktionen.");
        }

        List<MultipartFile> sanitizedFiles = files == null ? List.of()
            : files.stream().filter(file -> file != null && !file.isEmpty()).toList();

        if (sanitizedFiles.isEmpty()) {
            return new UploadOutcome(null, "Välj minst en fil att ladda upp.");
        }

        if (sanitizedFiles.size() > MAX_UPLOAD_FILES) {
            return new UploadOutcome(null,
                "Du kan ladda upp högst %d filer åt gången.".formatted(MAX_UPLOAD_FILES));
        }

        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);

        try {
            dev.pekelund.pklnd.storage.UploadResult uploadResult = storage.uploadFilesWithResults(sanitizedFiles, owner);
            List<StoredReceiptReference> uploadedReferences = uploadResult.uploadedReceipts();
            int uploadedCount = uploadedReferences.size();
            
            String successMessage = null;
            String errorMessage = null;

            // Build success message
            if (uploadedCount > 0) {
                successMessage = uploadedCount == 1
                    ? "1 fil laddades upp."
                    : "%d filer laddades upp.".formatted(uploadedCount);
            }

            // Notify parser about successful uploads
            if (uploadedCount > 0 && receiptProcessingClient.isPresent()) {
                ProcessingResult processingResult = receiptProcessingClient.get().notifyUploads(uploadedReferences);
                if (processingResult.succeededCount() > 0) {
                    int queued = processingResult.succeededCount();
                    successMessage = queued == 1
                        ? "1 fil laddades upp och köades för tolkning."
                        : "%d filer laddades upp och köades för tolkning.".formatted(queued);
                }
                if (!processingResult.failures().isEmpty()) {
                    String parsingErrors = formatProcessingFailure(processingResult.failures());
                    errorMessage = errorMessage != null ? errorMessage + " " + parsingErrors : parsingErrors;
                    LOGGER.warn("Failed to queue {} receipt(s) for parsing", processingResult.failures().size());
                }
            }

            // Add upload failure messages
            if (uploadResult.hasFailures()) {
                String uploadErrors = formatUploadFailures(uploadResult.failures());
                errorMessage = errorMessage != null ? errorMessage + " " + uploadErrors : uploadErrors;
            }

            // If nothing succeeded, return only error
            if (uploadedCount == 0 && uploadResult.hasFailures()) {
                return new UploadOutcome(null, errorMessage);
            }

            return new UploadOutcome(successMessage, errorMessage);
        } catch (ReceiptStorageException ex) {
            LOGGER.error("Failed to upload receipts", ex);
            return new UploadOutcome(null, ex.getMessage());
        }
    }

    private String formatUploadFailures(List<dev.pekelund.pklnd.storage.UploadFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return null;
        }

        long duplicateCount = failures.stream().filter(dev.pekelund.pklnd.storage.UploadFailure::isDuplicate).count();
        long errorCount = failures.size() - duplicateCount;

        StringBuilder message = new StringBuilder();
        
        if (duplicateCount > 0) {
            if (duplicateCount == 1) {
                String filename = failures.stream()
                    .filter(dev.pekelund.pklnd.storage.UploadFailure::isDuplicate)
                    .findFirst()
                    .map(dev.pekelund.pklnd.storage.UploadFailure::filename)
                    .orElse("okänd fil");
                message.append("Kvittot '").append(filename).append("' har redan laddats upp tidigare.");
            } else {
                message.append(duplicateCount).append(" kvitton har redan laddats upp tidigare.");
            }
        }
        
        if (errorCount > 0) {
            if (message.length() > 0) {
                message.append(" ");
            }
            message.append(errorCount == 1 ? "1 fil" : errorCount + " filer")
                .append(" kunde inte laddas upp på grund av ett fel.");
        }

        return message.toString();
    }

    private String formatProcessingFailure(List<ProcessingFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return null;
        }

        String joined = failures.stream()
            .map(failure -> failure.reference().objectName())
            .limit(3)
            .collect(Collectors.joining(", "));
        if (failures.size() > 3) {
            joined = joined + " …";
        }
        return "Vissa uppladdningar kunde inte köas för tolkning: %s.".formatted(joined);
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

