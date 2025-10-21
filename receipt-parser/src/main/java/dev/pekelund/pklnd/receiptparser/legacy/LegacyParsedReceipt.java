package dev.pekelund.pklnd.receiptparser.legacy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LegacyParsedReceipt(
    ReceiptFormat format,
    String storeName,
    LocalDate receiptDate,
    BigDecimal totalAmount,
    List<LegacyReceiptItem> items,
    List<LegacyReceiptVat> vats,
    List<LegacyReceiptDiscount> generalDiscounts,
    List<LegacyReceiptError> errors
) {
    public LegacyParsedReceipt {
        items = items == null ? List.of() : List.copyOf(items);
        vats = vats == null ? List.of() : List.copyOf(vats);
        generalDiscounts = generalDiscounts == null ? List.of() : List.copyOf(generalDiscounts);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
