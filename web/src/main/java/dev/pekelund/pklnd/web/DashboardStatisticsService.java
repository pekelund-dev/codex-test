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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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

        // Use lightweight summaries instead of full receipts
        List<ReceiptExtractionService.ReceiptSummary> allSummaries = receiptsEnabled ? loadAllReceiptSummaries() : List.of();
        long totalReceipts = receiptsEnabled ? allSummaries.size() : 0L;
        long totalStores = receiptsEnabled ? countDistinctStoresFromSummaries(allSummaries) : 0L;
        long totalItems = receiptsEnabled ? countItemsFromSummaries(allSummaries) : 0L;

        PersonalTotals personalTotals = receiptsEnabled
            ? computePersonalTotalsFromSummaries(authentication)
            : PersonalTotals.unavailable();

        return new DashboardStatistics(
            userCount,
            userCountAccurate,
            totalReceipts,
            totalStores,
            totalItems,
            receiptsEnabled,
            personalTotals.lastMonth(),
            personalTotals.currentMonth(),
            personalTotals.available()
        );
    }

    private List<ReceiptExtractionService.ReceiptSummary> loadAllReceiptSummaries() {
        try {
            return receiptExtractionService.get().listReceiptSummaries(null, true);
        } catch (ReceiptExtractionAccessException ex) {
            log.warn("Unable to load receipt summaries for dashboard statistics.", ex);
            return List.of();
        }
    }

    private long countDistinctStoresFromSummaries(List<ReceiptExtractionService.ReceiptSummary> summaries) {
        if (summaries.isEmpty()) {
            return 0L;
        }

        Set<String> stores = new HashSet<>();
        for (ReceiptExtractionService.ReceiptSummary summary : summaries) {
            String storeName = summary.storeName();
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

    private long countItemsFromSummaries(List<ReceiptExtractionService.ReceiptSummary> summaries) {
        return summaries.stream()
            .mapToLong(ReceiptExtractionService.ReceiptSummary::itemCount)
            .sum();
    }

    private PersonalTotals computePersonalTotalsFromSummaries(Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null || receiptExtractionService.isEmpty()) {
            return PersonalTotals.unavailable();
        }

        try {
            List<ReceiptExtractionService.ReceiptSummary> summaries = 
                receiptExtractionService.get().listReceiptSummaries(owner, false);
            if (summaries.isEmpty()) {
                return new PersonalTotals(true, BigDecimal.ZERO, BigDecimal.ZERO);
            }

            YearMonth currentMonth = YearMonth.now();
            YearMonth previousMonth = currentMonth.minusMonths(1);

            BigDecimal currentTotal = BigDecimal.ZERO;
            BigDecimal lastMonthTotal = BigDecimal.ZERO;

            for (ReceiptExtractionService.ReceiptSummary summary : summaries) {
                if (summary == null) {
                    continue;
                }

                BigDecimal amount = parseTotalAmount(summary.totalAmount());
                if (amount == null) {
                    continue;
                }

                LocalDate receiptDate = parseReceiptDate(summary.receiptDate())
                    .orElseGet(() -> deriveDateFromInstant(summary.updatedAt()));
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

    private BigDecimal parseTotalAmount(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String string) {
            String normalized = string.replace('\u00A0', ' ').trim();
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

    private record PersonalTotals(boolean available, BigDecimal lastMonth, BigDecimal currentMonth) {

        static PersonalTotals unavailable() {
            return new PersonalTotals(false, null, null);
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
        boolean personalTotalsAvailable
    ) {

        public String formatAmount(BigDecimal value) {
            if (value == null) {
                return "--";
            }
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
