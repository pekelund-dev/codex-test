package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class VatMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(VatMonitoringService.class);

    private static final BigDecimal OLD_VAT_RATE = new BigDecimal("12.0");
    private static final BigDecimal NEW_VAT_RATE = new BigDecimal("6.0");
    private static final BigDecimal VAT_CHANGE_CUTOFF = new BigDecimal("25.0");
    
    // Threshold for price increase tolerance (2%)
    private static final BigDecimal PRICE_INCREASE_THRESHOLD = new BigDecimal("0.02");
    
    // VAT change date: April 1, 2026
    private static final LocalDate VAT_CHANGE_DATE = LocalDate.of(2026, 4, 1);

    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;

    public VatMonitoringService(
        ReceiptExtractionService receiptExtractionService,
        ReceiptOwnerResolver receiptOwnerResolver
    ) {
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
        this.receiptOwnerResolver = receiptOwnerResolver;
    }

    public boolean isEnabled() {
        return receiptExtractionService
            .filter(ReceiptExtractionService::isEnabled)
            .isPresent();
    }

    public VatMonitoringResult analyzeVatChanges(Authentication authentication) {
        if (!isEnabled()) {
            return VatMonitoringResult.unavailable();
        }

        try {
            List<ParsedReceipt> receipts = loadReceipts(authentication);
            List<ItemPriceComparison> comparisons = analyzeItemPrices(receipts);
            
            long totalItemsTracked = comparisons.size();
            long itemsWithIncrease = comparisons.stream()
                .filter(ItemPriceComparison::hasSuspiciousIncrease)
                .count();
            
            return new VatMonitoringResult(
                true,
                comparisons,
                totalItemsTracked,
                itemsWithIncrease,
                VAT_CHANGE_DATE
            );
        } catch (Exception ex) {
            log.error("Failed to analyze VAT changes", ex);
            return VatMonitoringResult.unavailable();
        }
    }

    private List<ParsedReceipt> loadReceipts(Authentication authentication) {
        if (receiptExtractionService.isEmpty()) {
            return List.of();
        }

        ReceiptExtractionService service = receiptExtractionService.get();
        boolean isAdmin = authentication != null &&
            authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equalsIgnoreCase("ROLE_ADMIN"));

        if (isAdmin) {
            return service.listAllReceipts();
        }

        ReceiptOwner owner = receiptOwnerResolver.resolveOwner(authentication);
        return service.listReceiptsForOwner(owner);
    }

    private List<ItemPriceComparison> analyzeItemPrices(List<ParsedReceipt> receipts) {
        // Group items by EAN code
        Map<String, List<ItemOccurrence>> itemsByEan = new HashMap<>();

        for (ParsedReceipt receipt : receipts) {
            LocalDate receiptDate = parseReceiptDate(receipt.receiptDate());
            if (receiptDate == null) {
                continue;
            }

            // Check if receipt has food VAT rate (12%)
            boolean hasFoodVat = hasVatRate(receipt, OLD_VAT_RATE);
            if (!hasFoodVat) {
                continue;
            }

            List<Map<String, Object>> items = receipt.displayItems();
            for (Map<String, Object> item : items) {
                String ean = extractEan(item);
                if (ean == null || ean.isBlank()) {
                    continue;
                }

                String name = asString(item.get("name"));
                BigDecimal unitPrice = parseBigDecimal(item.get("unitPrice"));
                String storeName = receipt.storeName();

                if (name != null && unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                    ItemOccurrence occurrence = new ItemOccurrence(
                        ean,
                        name,
                        unitPrice,
                        receiptDate,
                        storeName != null ? storeName : "Okänd",
                        receipt.id()
                    );

                    itemsByEan.computeIfAbsent(ean, k -> new ArrayList<>()).add(occurrence);
                }
            }
        }

        // Calculate price comparisons for items with occurrences before and after VAT change
        List<ItemPriceComparison> comparisons = new ArrayList<>();

        for (Map.Entry<String, List<ItemOccurrence>> entry : itemsByEan.entrySet()) {
            String ean = entry.getKey();
            List<ItemOccurrence> occurrences = entry.getValue();

            if (occurrences.size() < 2) {
                continue; // Need at least 2 occurrences to compare
            }

            // Find latest price before VAT change and earliest price after
            Optional<ItemOccurrence> beforeOpt = occurrences.stream()
                .filter(o -> o.date().isBefore(VAT_CHANGE_DATE))
                .max(Comparator.comparing(ItemOccurrence::date));

            Optional<ItemOccurrence> afterOpt = occurrences.stream()
                .filter(o -> !o.date().isBefore(VAT_CHANGE_DATE))
                .min(Comparator.comparing(ItemOccurrence::date));

            if (beforeOpt.isPresent() && afterOpt.isPresent()) {
                ItemOccurrence before = beforeOpt.get();
                ItemOccurrence after = afterOpt.get();

                ItemPriceComparison comparison = createComparison(ean, before, after, occurrences);
                comparisons.add(comparison);
            }
        }

        // Sort by suspicious increases first, then by price deviation percentage
        comparisons.sort(Comparator
            .comparing(ItemPriceComparison::hasSuspiciousIncrease, Comparator.reverseOrder())
            .thenComparing(ItemPriceComparison::priceDeviationPercent, Comparator.reverseOrder()));

        return Collections.unmodifiableList(comparisons);
    }

    private ItemPriceComparison createComparison(
        String ean,
        ItemOccurrence before,
        ItemOccurrence after,
        List<ItemOccurrence> allOccurrences
    ) {
        BigDecimal priceBefore = before.unitPrice();
        BigDecimal priceAfter = after.unitPrice();

        // Calculate expected price after VAT reduction
        // Expected: (price / 1.12) * 1.06
        BigDecimal expectedPrice = priceBefore
            .divide(new BigDecimal("1.12"), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("1.06"))
            .setScale(2, RoundingMode.HALF_UP);

        // Calculate actual change
        BigDecimal priceChange = priceAfter.subtract(priceBefore);
        BigDecimal priceChangePercent = priceBefore.compareTo(BigDecimal.ZERO) > 0
            ? priceChange.divide(priceBefore, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
            : BigDecimal.ZERO;

        // Calculate expected change
        BigDecimal expectedChange = expectedPrice.subtract(priceBefore);
        BigDecimal expectedChangePercent = priceBefore.compareTo(BigDecimal.ZERO) > 0
            ? expectedChange.divide(priceBefore, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
            : BigDecimal.ZERO;

        // Check if actual increase is significantly higher than expected
        BigDecimal deviation = priceAfter.subtract(expectedPrice);
        BigDecimal deviationPercent = expectedPrice.compareTo(BigDecimal.ZERO) > 0
            ? deviation.divide(expectedPrice, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        boolean hasSuspiciousIncrease = deviationPercent.compareTo(PRICE_INCREASE_THRESHOLD) > 0;

        // Get all stores where item appeared
        List<String> stores = allOccurrences.stream()
            .map(ItemOccurrence::storeName)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        return new ItemPriceComparison(
            ean,
            after.name(),
            priceBefore,
            priceAfter,
            expectedPrice,
            priceChange,
            priceChangePercent,
            expectedChange,
            expectedChangePercent,
            deviation,
            deviationPercent.multiply(new BigDecimal("100")),
            hasSuspiciousIncrease,
            before.date(),
            after.date(),
            stores,
            before.storeName(),
            after.storeName(),
            allOccurrences.size()
        );
    }

    private boolean hasVatRate(ParsedReceipt receipt, BigDecimal targetRate) {
        List<Map<String, Object>> vats = receipt.vats();
        if (vats == null || vats.isEmpty()) {
            // If no VAT info, assume food items might be present
            return true;
        }

        for (Map<String, Object> vat : vats) {
            BigDecimal rate = parseBigDecimal(vat.get("rate"));
            if (rate != null && rate.compareTo(targetRate) == 0) {
                return true;
            }
            // Also check if rate is very close to avoid floating point issues
            if (rate != null && rate.subtract(targetRate).abs().compareTo(new BigDecimal("0.1")) < 0) {
                return true;
            }
        }

        return false;
    }

    private String extractEan(Map<String, Object> item) {
        // Try common EAN field names
        for (String key : List.of("eanCode", "ean", "barcode", "barCode", "ean_code", "EAN", "gtin")) {
            Object value = item.get(key);
            if (value != null) {
                String ean = value.toString().trim();
                if (!ean.isEmpty() && ean.matches("\\d{8,14}")) {
                    return ean;
                }
            }
        }
        return null;
    }

    private LocalDate parseReceiptDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(dateStr.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        if (value instanceof String str) {
            try {
                String normalized = str.replace(',', '.').trim();
                return new BigDecimal(normalized);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    public record VatMonitoringResult(
        boolean available,
        List<ItemPriceComparison> comparisons,
        long totalItemsTracked,
        long itemsWithSuspiciousIncrease,
        LocalDate vatChangeDate
    ) {
        public static VatMonitoringResult unavailable() {
            return new VatMonitoringResult(false, List.of(), 0, 0, VAT_CHANGE_DATE);
        }
    }

    public record ItemPriceComparison(
        String ean,
        String itemName,
        BigDecimal priceBefore,
        BigDecimal priceAfter,
        BigDecimal expectedPrice,
        BigDecimal priceChange,
        BigDecimal priceChangePercent,
        BigDecimal expectedChange,
        BigDecimal expectedChangePercent,
        BigDecimal priceDeviation,
        BigDecimal priceDeviationPercent,
        boolean hasSuspiciousIncrease,
        LocalDate dateBefore,
        LocalDate dateAfter,
        List<String> allStores,
        String storeNameBefore,
        String storeNameAfter,
        int totalOccurrences
    ) {
        public String formattedPriceBefore() {
            return formatPrice(priceBefore);
        }

        public String formattedPriceAfter() {
            return formatPrice(priceAfter);
        }

        public String formattedExpectedPrice() {
            return formatPrice(expectedPrice);
        }

        public String formattedPriceChange() {
            return formatPriceWithSign(priceChange);
        }

        public String formattedPriceDeviation() {
            return formatPriceWithSign(priceDeviation);
        }

        private String formatPrice(BigDecimal price) {
            if (price == null) {
                return "0.00";
            }
            return price.setScale(2, RoundingMode.HALF_UP).toPlainString() + " kr";
        }

        private String formatPriceWithSign(BigDecimal price) {
            if (price == null) {
                return "0.00 kr";
            }
            BigDecimal rounded = price.setScale(2, RoundingMode.HALF_UP);
            String sign = rounded.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
            return sign + rounded.toPlainString() + " kr";
        }
    }

    private record ItemOccurrence(
        String ean,
        String name,
        BigDecimal unitPrice,
        LocalDate date,
        String storeName,
        String receiptId
    ) {}
}
