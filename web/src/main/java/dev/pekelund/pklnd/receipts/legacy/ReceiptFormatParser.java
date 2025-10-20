package dev.pekelund.pklnd.receipts.legacy;

public interface ReceiptFormatParser {

    boolean supportsFormat(ReceiptFormat format);

    LegacyParsedReceipt parse(String[] pdfData, ReceiptFormat format);
}
