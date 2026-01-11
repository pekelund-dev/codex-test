package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.Category;
import dev.pekelund.pklnd.firestore.CategoryService;
import dev.pekelund.pklnd.firestore.ItemCategoryMapping;
import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for calculating statistics based on categories and tags.
 */
@Service
public class CategoryStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(CategoryStatisticsService.class);

    private final Optional<CategoryService> categoryService;
    private final Optional<ItemCategorizationService> itemCategorizationService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;

    public CategoryStatisticsService(
        Optional<CategoryService> categoryService,
        Optional<ItemCategorizationService> itemCategorizationService,
        Optional<ReceiptExtractionService> receiptExtractionService
    ) {
        this.categoryService = categoryService;
        this.itemCategorizationService = itemCategorizationService;
        this.receiptExtractionService = receiptExtractionService;
    }

    public boolean isEnabled() {
        return categoryService.isPresent() 
            && categoryService.get().isEnabled()
            && itemCategorizationService.isPresent()
            && itemCategorizationService.get().isEnabled()
            && receiptExtractionService.isPresent()
            && receiptExtractionService.get().isEnabled();
    }

    /**
     * Get spending statistics grouped by category for a specific owner.
     */
    public CategorySpendingStats getSpendingByCategory(ReceiptOwner owner) {
        if (!isEnabled()) {
            return CategorySpendingStats.empty();
        }

        List<ParsedReceipt> receipts = owner != null
            ? receiptExtractionService.get().listReceiptsForOwner(owner)
            : receiptExtractionService.get().listAllReceipts();

        return calculateCategoryStats(receipts);
    }

    /**
     * Get spending statistics grouped by category for a specific month.
     */
    public CategorySpendingStats getSpendingByCategoryForMonth(YearMonth yearMonth, ReceiptOwner owner) {
        if (!isEnabled()) {
            return CategorySpendingStats.empty();
        }

        List<ParsedReceipt> allReceipts = owner != null
            ? receiptExtractionService.get().listReceiptsForOwner(owner)
            : receiptExtractionService.get().listAllReceipts();

        List<ParsedReceipt> monthReceipts = allReceipts.stream()
            .filter(receipt -> {
                String dateStr = receipt.receiptDate();
                if (dateStr == null) return false;
                try {
                    LocalDate date = LocalDate.parse(dateStr);
                    return YearMonth.from(date).equals(yearMonth);
                } catch (Exception e) {
                    return false;
                }
            })
            .toList();

        return calculateCategoryStats(monthReceipts);
    }

    /**
     * Get spending statistics grouped by category for a specific year.
     */
    public CategorySpendingStats getSpendingByCategoryForYear(int year, ReceiptOwner owner) {
        if (!isEnabled()) {
            return CategorySpendingStats.empty();
        }

        List<ParsedReceipt> allReceipts = owner != null
            ? receiptExtractionService.get().listReceiptsForOwner(owner)
            : receiptExtractionService.get().listAllReceipts();

        List<ParsedReceipt> yearReceipts = allReceipts.stream()
            .filter(receipt -> {
                String dateStr = receipt.receiptDate();
                if (dateStr == null) return false;
                try {
                    LocalDate date = LocalDate.parse(dateStr);
                    return date.getYear() == year;
                } catch (Exception e) {
                    return false;
                }
            })
            .toList();

        return calculateCategoryStats(yearReceipts);
    }

    private CategorySpendingStats calculateCategoryStats(List<ParsedReceipt> receipts) {
        Map<String, CategorySpending> categorySpending = new HashMap<>();
        Map<String, Category> categoryCache = new HashMap<>();

        // Build category cache
        if (categoryService.isPresent()) {
            categoryService.get().listCategories().forEach(cat -> 
                categoryCache.put(cat.id(), cat)
            );
        }

        for (ParsedReceipt receipt : receipts) {
            String receiptId = receipt.id();
            List<ItemCategoryMapping> itemCategories = 
                itemCategorizationService.get().getCategoriesForReceipt(receiptId);

            // Create a map of item index/EAN to category ID
            Map<String, String> itemToCategoryMap = itemCategories.stream()
                .collect(Collectors.toMap(
                    this::getItemIdentifier,
                    ItemCategoryMapping::categoryId,
                    (existing, replacement) -> existing
                ));

            // Process each item in the receipt
            List<Map<String, Object>> items = receipt.displayItems();
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                String itemIdentifier = getItemIdentifierFromMap(item, i);
                
                String categoryId = itemToCategoryMap.get(itemIdentifier);
                if (categoryId == null) {
                    continue; // Item not categorized
                }

                Category category = categoryCache.get(categoryId);
                if (category == null) {
                    continue; // Category not found
                }

                BigDecimal itemPrice = extractPrice(item);
                if (itemPrice == null) {
                    continue; // No price
                }

                // Update category spending
                CategorySpending spending = categorySpending.computeIfAbsent(
                    categoryId,
                    id -> new CategorySpending(category.name(), category.parentId())
                );
                spending.addAmount(itemPrice);
                spending.incrementItemCount();
            }
        }

        // Calculate totals
        BigDecimal totalSpending = categorySpending.values().stream()
            .map(CategorySpending::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group by parent category for hierarchical view
        Map<String, List<CategorySpending>> byParent = categorySpending.values().stream()
            .collect(Collectors.groupingBy(
                cs -> cs.parentId() != null ? cs.parentId() : "TOP_LEVEL",
                Collectors.toList()
            ));

        return new CategorySpendingStats(
            Collections.unmodifiableMap(categorySpending),
            Collections.unmodifiableMap(byParent),
            totalSpending
        );
    }

    /**
     * Get the item identifier from a mapping (prefer EAN over index).
     */
    private String getItemIdentifier(ItemCategoryMapping mapping) {
        return mapping.itemEan() != null ? mapping.itemEan() : mapping.itemIndex();
    }

    /**
     * Get the item identifier from a receipt item map with its index.
     */
    private String getItemIdentifierFromMap(Map<String, Object> item, int index) {
        String itemEan = extractEan(item);
        return itemEan != null ? itemEan : String.valueOf(index);
    }

    private String extractEan(Map<String, Object> item) {
        Object ean = item.get("normalizedEan");
        return ean != null ? ean.toString() : null;
    }

    private BigDecimal extractPrice(Map<String, Object> item) {
        Object totalPrice = item.get("totalPrice");
        if (totalPrice == null) {
            return null;
        }
        
        if (totalPrice instanceof BigDecimal bd) {
            return bd;
        }
        
        if (totalPrice instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        
        try {
            return new BigDecimal(totalPrice.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record CategorySpendingStats(
        Map<String, CategorySpending> byCategory,
        Map<String, List<CategorySpending>> byParentCategory,
        BigDecimal totalSpending
    ) {
        public static CategorySpendingStats empty() {
            return new CategorySpendingStats(Map.of(), Map.of(), BigDecimal.ZERO);
        }

        public String formattedTotal() {
            return totalSpending.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }

    public static class CategorySpending {
        private final String categoryName;
        private final String parentId;
        private BigDecimal amount;
        private int itemCount;

        public CategorySpending(String categoryName, String parentId) {
            this.categoryName = categoryName;
            this.parentId = parentId;
            this.amount = BigDecimal.ZERO;
            this.itemCount = 0;
        }

        public void addAmount(BigDecimal additionalAmount) {
            this.amount = this.amount.add(additionalAmount);
        }

        public void incrementItemCount() {
            this.itemCount++;
        }

        public String categoryName() {
            return categoryName;
        }

        public String parentId() {
            return parentId;
        }

        public BigDecimal amount() {
            return amount;
        }

        public int itemCount() {
            return itemCount;
        }

        public String formattedAmount() {
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
