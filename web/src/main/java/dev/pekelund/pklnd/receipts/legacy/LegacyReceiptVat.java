package dev.pekelund.pklnd.receipts.legacy;

import java.math.BigDecimal;

public record LegacyReceiptVat(
    BigDecimal rate,
    BigDecimal taxAmount,
    BigDecimal netAmount,
    BigDecimal grossAmount
) {
}
