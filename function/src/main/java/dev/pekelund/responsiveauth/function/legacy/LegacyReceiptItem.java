package dev.pekelund.responsiveauth.function.legacy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LegacyReceiptItem {

    private final String name;
    private final String eanCode;
    private final BigDecimal unitPrice;
    private final String quantity;
    private final BigDecimal totalPrice;
    private final List<LegacyReceiptDiscount> discounts = new ArrayList<>();

    public LegacyReceiptItem(String name, String eanCode, BigDecimal unitPrice, String quantity, BigDecimal totalPrice) {
        this.name = name;
        this.eanCode = eanCode;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
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
        return Collections.unmodifiableList(discounts);
    }

    public void addDiscount(LegacyReceiptDiscount discount) {
        if (discount != null) {
            discounts.add(discount);
        }
    }
}
