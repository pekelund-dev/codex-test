package dev.pekelund.responsiveauth.function.legacy;

public interface ReceiptFormatParser {

    boolean supportsFormat(ReceiptFormat format);

    LegacyParsedReceipt parse(String[] pdfData, ReceiptFormat format);
}
