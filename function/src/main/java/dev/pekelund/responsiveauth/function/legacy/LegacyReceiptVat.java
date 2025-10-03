package dev.pekelund.responsiveauth.function.legacy;

import java.math.BigDecimal;

public record LegacyReceiptVat(
    BigDecimal rate,
    BigDecimal taxAmount,
    BigDecimal netAmount,
    BigDecimal grossAmount
) {
}
