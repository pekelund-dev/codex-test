package dev.pekelund.pklnd.firestore;

import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReceiptSearchService {

    private final ReceiptRepository receiptRepository;

    public ReceiptSearchService(ReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    public List<ParsedReceipt> searchByItemName(String searchQuery, ReceiptOwner owner, boolean includeAllOwners) {
        if (!receiptRepository.isEnabled() || !StringUtils.hasText(searchQuery)) {
            return List.of();
        }

        if (!includeAllOwners && owner == null) {
            return List.of();
        }

        List<ParsedReceipt> allReceipts = includeAllOwners ? receiptRepository.listAllReceipts() : receiptRepository.listReceiptsForOwner(owner);

        String normalizedQuery = searchQuery.trim().toLowerCase(Locale.ROOT);

        List<ParsedReceipt> matchingReceipts = new ArrayList<>();
        for (ParsedReceipt receipt : allReceipts) {
            if (receipt == null) {
                continue;
            }

            List<Map<String, Object>> items = receipt.displayItems();
            if (items == null || items.isEmpty()) {
                continue;
            }

            boolean hasMatch = false;
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }

                Object nameObj = item.get("name");
                if (nameObj == null) {
                    continue;
                }

                String itemName = nameObj.toString().toLowerCase(Locale.ROOT);
                if (itemName.contains(normalizedQuery)) {
                    hasMatch = true;
                    break;
                }
            }

            if (hasMatch) {
                matchingReceipts.add(receipt);
            }
        }

        matchingReceipts.sort(Comparator.comparing(ParsedReceipt::updatedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return Collections.unmodifiableList(matchingReceipts);
    }

    public List<SearchItemResult> searchItemsByName(String searchQuery, ReceiptOwner owner, boolean includeAllOwners) {
        if (!receiptRepository.isEnabled() || !StringUtils.hasText(searchQuery)) {
            return List.of();
        }

        if (!includeAllOwners && owner == null) {
            return List.of();
        }

        List<ParsedReceipt> allReceipts = includeAllOwners ? receiptRepository.listAllReceipts() : receiptRepository.listReceiptsForOwner(owner);

        String normalizedQuery = searchQuery.trim().toLowerCase(Locale.ROOT);

        List<SearchItemResult> matchingItems = new ArrayList<>();
        for (ParsedReceipt receipt : allReceipts) {
            if (receipt == null) {
                continue;
            }

            List<Map<String, Object>> items = receipt.displayItems();
            if (items == null || items.isEmpty()) {
                continue;
            }

            String receiptDisplayName = receipt.displayName();
            if (receiptDisplayName == null || receiptDisplayName.isBlank()) {
                receiptDisplayName = receipt.objectPath();
            }

            String storeName = receipt.storeName();
            if (storeName == null || storeName.isBlank()) {
                storeName = "Unknown";
            }

            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }

                Object nameObj = item.get("name");
                if (nameObj == null) {
                    continue;
                }

                String itemName = nameObj.toString();
                if (itemName.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                    String price = asString(item.get("displayUnitPrice"));
                    BigDecimal priceValue = parseBigDecimal(item.get("unitPrice"));
                    String quantity = asString(item.get("displayQuantity"));
                    String total = asString(item.get("displayTotalPrice"));
                    BigDecimal totalValue = parseBigDecimal(item.get("totalPrice"));

                    BigDecimal discountValue = ParsedReceipt.calculateItemDiscountTotal(item);
                    String discount = discountValue != null && discountValue.compareTo(BigDecimal.ZERO) > 0
                        ? formatAmount(discountValue) : null;

                    matchingItems.add(new SearchItemResult(
                        receipt.id(),
                        receiptDisplayName,
                        storeName,
                        receipt.receiptDate(),
                        receipt.updatedAt(),
                        itemName,
                        price,
                        priceValue,
                        quantity,
                        total,
                        totalValue,
                        discount,
                        discountValue
                    ));
                }
            }
        }

        matchingItems.sort(Comparator
            .comparing(SearchItemResult::receiptDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SearchItemResult::itemName, String.CASE_INSENSITIVE_ORDER));

        return Collections.unmodifiableList(matchingItems);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return org.springframework.util.StringUtils.hasText(string) ? string : null;
        }
        return value.toString();
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString().replace(',', '.');
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
