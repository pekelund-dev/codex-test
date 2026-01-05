package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionAccessException;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.firestore.FirestoreUserService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(DashboardStatisticsService.class);

    private final FirestoreUserService firestoreUserService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;

    public DashboardStatisticsService(FirestoreUserService firestoreUserService,
                                      @Autowired(required = false) ReceiptExtractionService receiptExtractionService,
                                      ReceiptOwnerResolver receiptOwnerResolver) {
        this.firestoreUserService = firestoreUserService;
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
        this.receiptOwnerResolver = receiptOwnerResolver;
    }

    public DashboardStatistics loadStatistics(Authentication authentication) {
        long userCount = firestoreUserService.countUsers();
        boolean userCountAccurate = firestoreUserService.isEnabled();

        boolean receiptsEnabled = receiptExtractionService
            .filter(ReceiptExtractionService::isEnabled)
            .isPresent();

        List<ParsedReceipt> allReceipts = receiptsEnabled ? loadAllReceipts() : List.of();
        long totalReceipts = receiptsEnabled ? allReceipts.size() : 0L;
        long totalStores = receiptsEnabled ? countDistinctStores(allReceipts) : 0L;
        long totalItems = receiptsEnabled ? countItems(allReceipts) : 0L;

        PersonalTotals personalTotals = receiptsEnabled
            ? computePersonalTotals(authentication)
            : PersonalTotals.unavailable();

        YearlyStatistics yearlyStats = receiptsEnabled
            ? computeYearlyStatistics(authentication)
            : YearlyStatistics.unavailable();

        return new DashboardStatistics(
            userCount,
            userCountAccurate,
            totalReceipts,
            totalStores,
            totalItems,
            receiptsEnabled,
            personalTotals.lastMonth(),
            personalTotals.currentMonth(),
            personalTotals.available(),
            yearlyStats.yearlyTotals(),
            yearlyStats.monthlyTotals(),
            yearlyStats.yearlyDiscounts(),
            yearlyStats.monthlyDiscounts(),
            yearlyStats.available()
        );
    }

    private List<ParsedReceipt> loadAllReceipts() {
        try {
            return receiptExtractionService.get().listAllReceipts();
        } catch (ReceiptExtractionAccessException ex) {
            log.warn("Unable to load parsed receipts for dashboard statistics.", ex);
            return List.of();
        }
    }

    private long countDistinctStores(List<ParsedReceipt> receipts) {
        if (receipts.isEmpty()) {
            return 0L;
        }

        Set<String> stores = new HashSet<>();
        for (ParsedReceipt receipt : receipts) {
            String storeName = receipt != null ? receipt.storeName() : null;
            if (storeName == null) {
                continue;
            }
            String normalizedStoreName = storeName.trim();
            if (!StringUtils.hasText(normalizedStoreName)) {
                continue;
            }
            stores.add(normalizedStoreName.toLowerCase(Locale.ROOT));
        }
        return stores.size();
    }

    /**
     * Get statistics for all stores with receipt counts.
     * Returns a list of StoreStatistic objects sorted by receipt count descending.
     */
    public List<StoreStatistic> getStoreStatistics(Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null || receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            return List.of();
        }

        try {
            List<ParsedReceipt> receipts = receiptExtractionService.get().listReceiptsForOwner(owner);
            if (receipts.isEmpty()) {
                return List.of();
            }

            // Group receipts by store name
            Map<String, List<ParsedReceipt>> receiptsByStore = new HashMap<>();
            for (ParsedReceipt receipt : receipts) {
                String storeName = receipt != null ? receipt.storeName() : null;
                if (storeName == null || !StringUtils.hasText(storeName.trim())) {
                    continue;
                }
                String normalizedStoreName = storeName.trim();
                receiptsByStore.computeIfAbsent(normalizedStoreName, k -> new ArrayList<>()).add(receipt);
            }

            // Calculate statistics for each store
            List<StoreStatistic> storeStats = new ArrayList<>();
            for (Map.Entry<String, List<ParsedReceipt>> entry : receiptsByStore.entrySet()) {
                String storeName = entry.getKey();
                List<ParsedReceipt> storeReceipts = entry.getValue();
                long receiptCount = storeReceipts.size();

                // Calculate total spending for this store
                BigDecimal totalSpending = BigDecimal.ZERO;
                for (ParsedReceipt receipt : storeReceipts) {
                    BigDecimal amount = receipt.totalAmountValue();
                    if (amount != null) {
                        totalSpending = totalSpending.add(amount);
                    }
                }

                storeStats.add(new StoreStatistic(storeName, receiptCount, totalSpending));
            }

            // Sort by receipt count descending
            storeStats.sort(Comparator.comparingLong(StoreStatistic::receiptCount).reversed());
            return Collections.unmodifiableList(storeStats);
        } catch (ReceiptExtractionAccessException ex) {
            log.warn("Unable to load store statistics.", ex);
            return List.of();
        }
    }

    /**
     * Get receipts for a specific store.
     */
    public List<ParsedReceipt> getReceiptsForStore(String storeName, Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null || receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            return List.of();
        }

        if (!StringUtils.hasText(storeName)) {
            return List.of();
        }

        try {
            List<ParsedReceipt> allReceipts = receiptExtractionService.get().listReceiptsForOwner(owner);
            String normalizedSearchName = storeName.trim();

            return allReceipts.stream()
                .filter(receipt -> {
                    String receiptStoreName = receipt.storeName();
                    return receiptStoreName != null && 
                           receiptStoreName.trim().equalsIgnoreCase(normalizedSearchName);
                })
                .collect(Collectors.toList());
        } catch (ReceiptExtractionAccessException ex) {
            log.warn("Unable to load receipts for store: " + storeName, ex);
            return List.of();
        }
    }

    public record StoreStatistic(String storeName, long receiptCount, BigDecimal totalSpending) {
        public String formatAmount() {
            if (totalSpending == null) {
                return "0.00";
            }
            return totalSpending.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }

    /**
     * Get receipts for a specific year.
     */
    public List<ParsedReceipt> getReceiptsForYear(int year, Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null || receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            return List.of();
        }

        try {
            List<ParsedReceipt> allReceipts = receiptExtractionService.get().listReceiptsForOwner(owner);
            
            return allReceipts.stream()
                .filter(receipt -> {
                    LocalDate receiptDate = parseReceiptDate(receipt.receiptDate())
                        .orElseGet(() -> deriveDateFromInstant(receipt.updatedAt()));
                    return receiptDate != null && receiptDate.getYear() == year;
                })
                .collect(Collectors.toList());
        } catch (ReceiptExtractionAccessException ex) {
            log.warn("Unable to load receipts for year: " + year, ex);
            return List.of();
        }
    }

    /**
     * Get receipts for a specific year and month.
     */
    public List<ParsedReceipt> getReceiptsForYearMonth(int year, int month, Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null || receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            return List.of();
        }

        try {
            List<ParsedReceipt> allReceipts = receiptExtractionService.get().listReceiptsForOwner(owner);
            
            return allReceipts.stream()
                .filter(receipt -> {
                    LocalDate receiptDate = parseReceiptDate(receipt.receiptDate())
                        .orElseGet(() -> deriveDateFromInstant(receipt.updatedAt()));
                    if (receiptDate == null) {
                        return false;
                    }
                    return receiptDate.getYear() == year && receiptDate.getMonthValue() == month;
                })
                .collect(Collectors.toList());
        } catch (ReceiptExtractionAccessException ex) {
            log.warn("Unable to load receipts for year: " + year + " month: " + month, ex);
            return List.of();
        }
    }

    /**
     * Calculate total discount savings from a list of receipts.
     */
    public BigDecimal calculateTotalDiscounts(List<ParsedReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ParsedReceipt receipt : receipts) {
            if (receipt == null) {
                continue;
            }
            BigDecimal discountAmount = receipt.totalDiscountAmount();
            if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(discountAmount);
            }
        }
        return total;
    }

    /**
     * Format a discount amount for display.
     */
    public String formatDiscountAmount(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Apply filters to a list of receipts.
     * Filters can be applied by start date, end date, and store name.
     */
    public List<ParsedReceipt> applyFilters(List<ParsedReceipt> receipts, String startDate, String endDate, String storeName) {
        if (receipts == null || receipts.isEmpty()) {
            return receipts;
        }

        return receipts.stream()
            .filter(receipt -> {
                // Filter by start date
                if (StringUtils.hasText(startDate)) {
                    LocalDate start = parseDate(startDate);
                    LocalDate receiptDate = parseReceiptDate(receipt.receiptDate())
                        .orElseGet(() -> deriveDateFromInstant(receipt.updatedAt()));
                    if (start != null && receiptDate != null && receiptDate.isBefore(start)) {
                        return false;
                    }
                }

                // Filter by end date
                if (StringUtils.hasText(endDate)) {
                    LocalDate end = parseDate(endDate);
                    LocalDate receiptDate = parseReceiptDate(receipt.receiptDate())
                        .orElseGet(() -> deriveDateFromInstant(receipt.updatedAt()));
                    if (end != null && receiptDate != null && receiptDate.isAfter(end)) {
                        return false;
                    }
                }

                // Filter by store name
                if (StringUtils.hasText(storeName)) {
                    String receiptStoreName = receipt.storeName();
                    if (receiptStoreName == null || 
                        !receiptStoreName.toLowerCase(Locale.ROOT).contains(storeName.trim().toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                }

                return true;
            })
            .collect(Collectors.toList());
    }

    private LocalDate parseDate(String dateString) {
        if (!StringUtils.hasText(dateString)) {
            return null;
        }
        try {
            return LocalDate.parse(dateString);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date: " + dateString, e);
            return null;
        }
    }

    private long countItems(List<ParsedReceipt> receipts) {
        return receipts.stream()
            .filter(receipt -> receipt != null && receipt.items() != null)
            .mapToLong(receipt -> receipt.items().size())
            .sum();
    }

    private PersonalTotals computePersonalTotals(Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null || receiptExtractionService.isEmpty()) {
            return PersonalTotals.unavailable();
        }

        try {
            List<ParsedReceipt> receipts = receiptExtractionService.get().listReceiptsForOwner(owner);
            if (receipts.isEmpty()) {
                return new PersonalTotals(true, BigDecimal.ZERO, BigDecimal.ZERO);
            }

            YearMonth currentMonth = YearMonth.now();
            YearMonth previousMonth = currentMonth.minusMonths(1);

            BigDecimal currentTotal = BigDecimal.ZERO;
            BigDecimal lastMonthTotal = BigDecimal.ZERO;

            for (ParsedReceipt receipt : receipts) {
                if (receipt == null) {
                    continue;
                }

                BigDecimal amount = receipt.totalAmountValue();
                if (amount == null) {
                    continue;
                }

                LocalDate receiptDate = parseReceiptDate(receipt.receiptDate())
                    .orElseGet(() -> deriveDateFromInstant(receipt.updatedAt()));
                if (receiptDate == null) {
                    continue;
                }

                YearMonth receiptMonth = YearMonth.from(receiptDate);
                if (receiptMonth.equals(currentMonth)) {
                    currentTotal = currentTotal.add(amount);
                } else if (receiptMonth.equals(previousMonth)) {
                    lastMonthTotal = lastMonthTotal.add(amount);
                }
            }

            return new PersonalTotals(true, lastMonthTotal, currentTotal);
        } catch (ReceiptExtractionAccessException ex) {
            log.warn("Unable to load personal receipt totals for dashboard statistics.", ex);
            return PersonalTotals.unavailable();
        }
    }

    private Optional<LocalDate> parseReceiptDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(rawDate));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private LocalDate deriveDateFromInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private YearlyStatistics computeYearlyStatistics(Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null || receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            return YearlyStatistics.unavailable();
        }

        try {
            List<ParsedReceipt> receipts = receiptExtractionService.get().listReceiptsForOwner(owner);
            if (receipts.isEmpty()) {
                return new YearlyStatistics(true, Map.of(), Map.of(), Map.of(), Map.of());
            }

            // Map to store year -> total amount
            Map<Integer, BigDecimal> yearlyTotals = new TreeMap<>(Comparator.reverseOrder());
            // Map to store year -> (month -> total amount)
            Map<Integer, Map<Month, BigDecimal>> monthlyByYear = new TreeMap<>(Comparator.reverseOrder());
            // Map to store year -> total discount savings
            Map<Integer, BigDecimal> yearlyDiscounts = new TreeMap<>(Comparator.reverseOrder());
            // Map to store year -> (month -> total discount savings)
            Map<Integer, Map<Month, BigDecimal>> monthlyDiscountsByYear = new TreeMap<>(Comparator.reverseOrder());

            for (ParsedReceipt receipt : receipts) {
                if (receipt == null) {
                    continue;
                }

                BigDecimal amount = receipt.totalAmountValue();
                BigDecimal discountAmount = receipt.totalDiscountAmount();

                LocalDate receiptDate = parseReceiptDate(receipt.receiptDate())
                    .orElseGet(() -> deriveDateFromInstant(receipt.updatedAt()));
                if (receiptDate == null) {
                    continue;
                }

                int year = receiptDate.getYear();
                Month month = receiptDate.getMonth();

                // Update yearly total
                if (amount != null) {
                    yearlyTotals.merge(year, amount, BigDecimal::add);
                }

                // Update monthly total for the year
                if (amount != null) {
                    monthlyByYear.computeIfAbsent(year, k -> new TreeMap<>())
                        .merge(month, amount, BigDecimal::add);
                }

                // Update yearly discount total
                if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                    yearlyDiscounts.merge(year, discountAmount, BigDecimal::add);
                }

                // Update monthly discount total for the year
                if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                    monthlyDiscountsByYear.computeIfAbsent(year, k -> new TreeMap<>())
                        .merge(month, discountAmount, BigDecimal::add);
                }
            }

            // Convert the nested maps to immutable maps while preserving order
            Map<Integer, Map<Month, BigDecimal>> unmodifiableMonthly = new TreeMap<>(Comparator.reverseOrder());
            for (Map.Entry<Integer, Map<Month, BigDecimal>> entry : monthlyByYear.entrySet()) {
                unmodifiableMonthly.put(entry.getKey(), Collections.unmodifiableMap(new TreeMap<>(entry.getValue())));
            }

            Map<Integer, Map<Month, BigDecimal>> unmodifiableMonthlyDiscounts = new TreeMap<>(Comparator.reverseOrder());
            for (Map.Entry<Integer, Map<Month, BigDecimal>> entry : monthlyDiscountsByYear.entrySet()) {
                unmodifiableMonthlyDiscounts.put(entry.getKey(), Collections.unmodifiableMap(new TreeMap<>(entry.getValue())));
            }

            return new YearlyStatistics(
                true,
                Collections.unmodifiableMap(yearlyTotals),
                Collections.unmodifiableMap(unmodifiableMonthly),
                Collections.unmodifiableMap(yearlyDiscounts),
                Collections.unmodifiableMap(unmodifiableMonthlyDiscounts)
            );
        } catch (ReceiptExtractionAccessException ex) {
            log.warn("Unable to load yearly statistics for dashboard.", ex);
            return YearlyStatistics.unavailable();
        }
    }

    private record PersonalTotals(boolean available, BigDecimal lastMonth, BigDecimal currentMonth) {

        static PersonalTotals unavailable() {
            return new PersonalTotals(false, null, null);
        }
    }

    private record YearlyStatistics(
        boolean available,
        Map<Integer, BigDecimal> yearlyTotals,
        Map<Integer, Map<Month, BigDecimal>> monthlyTotals,
        Map<Integer, BigDecimal> yearlyDiscounts,
        Map<Integer, Map<Month, BigDecimal>> monthlyDiscounts
    ) {

        static YearlyStatistics unavailable() {
            return new YearlyStatistics(false, Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    public record DashboardStatistics(
        long totalUsers,
        boolean userCountAccurate,
        long totalReceipts,
        long totalStores,
        long totalItems,
        boolean receiptDataAvailable,
        BigDecimal lastMonthTotal,
        BigDecimal currentMonthTotal,
        boolean personalTotalsAvailable,
        Map<Integer, BigDecimal> yearlyTotals,
        Map<Integer, Map<Month, BigDecimal>> monthlyTotals,
        Map<Integer, BigDecimal> yearlyDiscounts,
        Map<Integer, Map<Month, BigDecimal>> monthlyDiscounts,
        boolean yearlyStatisticsAvailable
    ) {

        public String formatAmount(BigDecimal value) {
            if (value == null) {
                return "--";
            }
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
