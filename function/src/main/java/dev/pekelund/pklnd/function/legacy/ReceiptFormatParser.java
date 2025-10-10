package dev.pekelund.pklnd.function.legacy;

public interface ReceiptFormatParser {

    boolean supportsFormat(ReceiptFormat format);

    LegacyParsedReceipt parse(String[] pdfData, ReceiptFormat format);
}
