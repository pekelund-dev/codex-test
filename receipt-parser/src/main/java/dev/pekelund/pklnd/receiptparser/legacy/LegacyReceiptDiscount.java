package dev.pekelund.pklnd.receiptparser.legacy;

import java.math.BigDecimal;

public record LegacyReceiptDiscount(String description, BigDecimal amount) { }
