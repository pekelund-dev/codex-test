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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        if (owner == null || receiptExtractionService.isEmpty()) {
            return YearlyStatistics.unavailable();
        }

        try {
            List<ParsedReceipt> receipts = receiptExtractionService.get().listReceiptsForOwner(owner);
            if (receipts.isEmpty()) {
                return new YearlyStatistics(true, Map.of(), Map.of());
            }

            // Map to store year -> total amount
            Map<Integer, BigDecimal> yearlyTotals = new TreeMap<>(Comparator.reverseOrder());
            // Map to store year -> (month -> total amount)
            Map<Integer, Map<Month, BigDecimal>> monthlyByYear = new TreeMap<>(Comparator.reverseOrder());

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

                int year = receiptDate.getYear();
                Month month = receiptDate.getMonth();

                // Update yearly total
                yearlyTotals.merge(year, amount, BigDecimal::add);

                // Update monthly total for the year
                monthlyByYear.computeIfAbsent(year, k -> new TreeMap<>())
                    .merge(month, amount, BigDecimal::add);
            }

            // Convert the nested map to immutable maps
            Map<Integer, Map<Month, BigDecimal>> unmodifiableMonthly = new TreeMap<>(Comparator.reverseOrder());
            for (Map.Entry<Integer, Map<Month, BigDecimal>> entry : monthlyByYear.entrySet()) {
                unmodifiableMonthly.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
            }

            return new YearlyStatistics(
                true,
                Collections.unmodifiableMap(yearlyTotals),
                Collections.unmodifiableMap(unmodifiableMonthly)
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
        Map<Integer, Map<Month, BigDecimal>> monthlyTotals
    ) {

        static YearlyStatistics unavailable() {
            return new YearlyStatistics(false, Map.of(), Map.of());
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
