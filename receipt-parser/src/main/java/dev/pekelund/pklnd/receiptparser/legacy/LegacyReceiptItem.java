package dev.pekelund.pklnd.receiptparser.legacy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LegacyReceiptItem(
    String name,
    String eanCode,
    BigDecimal unitPrice,
    String quantity,
    BigDecimal totalPrice,
    List<LegacyReceiptDiscount> discounts
) {

    public LegacyReceiptItem {
        discounts = discounts == null ? new ArrayList<>() : new ArrayList<>(discounts);
    }

    public LegacyReceiptItem(String name, String eanCode, BigDecimal unitPrice, String quantity, BigDecimal totalPrice) {
        this(name, eanCode, unitPrice, quantity, totalPrice, new ArrayList<>());
    }

    public String getName() {
        return name;
    }

    public String getEanCode() {
        return eanCode;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public List<LegacyReceiptDiscount> getDiscounts() {
        return Collections.unmodifiableList(this.discounts);
    }

    @Override
    public List<LegacyReceiptDiscount> discounts() {
        return getDiscounts();
    }

    public void addDiscount(LegacyReceiptDiscount discount) {
        if (discount != null) {
            this.discounts.add(discount);
        }
    }
}
