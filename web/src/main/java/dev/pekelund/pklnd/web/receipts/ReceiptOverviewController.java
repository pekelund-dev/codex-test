package dev.pekelund.pklnd.web.receipts;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionAccessException;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptFile;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptOwnerMatcher;
import dev.pekelund.pklnd.storage.ReceiptStorageException;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.web.ReceiptOwnerResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ReceiptOverviewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptOverviewController.class);
    private static final Pattern EAN_PATTERN = Pattern.compile("(\\d{8,14})");
    private static final Pattern QUANTITY_VALUE_PATTERN = Pattern.compile("([-+]?\\d+(?:[.,]\\d+)?)");
    private static final List<String> POSSIBLE_EAN_KEYS = List.of(
        "eanCode", "ean", "barcode", "barCode", "ean_code", "EAN", "gtin", "itemEan", "sku"
    );
    private static final Pattern WEEK_IDENTIFIER_PATTERN = Pattern.compile("^(\\d{4})-W(\\d{2})$");

    private enum PeriodType {
        WEEK,
        MONTH
    }

    private final Optional<ReceiptStorageService> receiptStorageService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;
    private final ReceiptScopeHelper scopeHelper;

    public ReceiptOverviewController(
        @Autowired(required = false) ReceiptStorageService receiptStorageService,
        @Autowired(required = false) ReceiptExtractionService receiptExtractionService,
        ReceiptOwnerResolver receiptOwnerResolver,
        ReceiptScopeHelper scopeHelper
    ) {
        this.receiptStorageService = Optional.ofNullable(receiptStorageService);
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
        this.receiptOwnerResolver = receiptOwnerResolver;
        this.scopeHelper = scopeHelper;
    }

    @GetMapping("/receipts/overview")
    public String receiptOverview(
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        ReceiptViewScope scope = scopeHelper.resolveScope(scopeParam, authentication);
        ReceiptPageData pageData = loadReceiptPageData(authentication, scope);
        boolean canViewAll = scopeHelper.isAdmin(authentication);

        LocalDate today = LocalDate.now();

        model.addAttribute("pageTitleKey", "page.receipts.overview.title");
        model.addAttribute("scopeParam", scopeHelper.toScopeParameter(scope));
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

    @GetMapping(value = "/receipts/overview/data", produces = "application/json")
    @ResponseBody
    public ResponseEntity<ReceiptOverviewResponse> receiptOverviewData(
        @RequestParam(value = "periodType", required = false) String periodTypeParam,
        @RequestParam(value = "primary", required = false) String primaryIdentifier,
        @RequestParam(value = "compare", required = false) String compareIdentifier,
        @RequestParam(value = "scope", required = false) String scopeParam,
        Authentication authentication
    ) {
        ReceiptViewScope scope = scopeHelper.resolveScope(scopeParam, authentication);
        ReceiptPageData pageData = loadReceiptPageData(authentication, scope);

        if (!pageData.parsedReceiptsEnabled()) {
            return ResponseEntity.ok(
                new ReceiptOverviewResponse(
                    false,
                    "Kvittotolkning måste vara aktiverad för att visa översikten.",
                    null,
                    null,
                    scopeHelper.toScopeParameter(scope),
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
                    scopeHelper.toScopeParameter(scope),
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
                    scopeHelper.toScopeParameter(scope),
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
                        scopeHelper.toScopeParameter(scope),
                        pageData.viewingAll(),
                        List.of()
                    )
                );
            }
        }

        List<ParsedReceipt> receipts = pageData.parsedReceipts();
        String scopeValue = scopeHelper.toScopeParameter(scope);

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
        boolean viewingAll = scopeHelper.isViewingAll(scope, authentication);

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
                null, null, null, null, null, null, null, null, 0, List.of(), List.of(), null
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
        return updatedAt != null ? updatedAt.toString() : null;
    }

    private String resolveReceiptDisplayName(ParsedReceipt receipt) {
        if (receipt == null) {
            return null;
        }
        String resolved = resolveReceiptDisplayNameParts(receipt.displayName(), receipt.storeName(), receipt.objectName());
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        if (StringUtils.hasText(receipt.objectPath())) {
            return receipt.objectPath();
        }
        return receipt.id();
    }

    private String resolveReceiptDisplayNameParts(String displayName, String storeName, String objectName) {
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

    // -------------------------------------------------------------------------
    // Records
    // -------------------------------------------------------------------------

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
            BigDecimal totalQuantity = quantityTotal;
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
}
