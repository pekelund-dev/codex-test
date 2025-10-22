package dev.pekelund.pklnd.receiptparser.legacy;

public interface ReceiptFormatParser {

    boolean supportsFormat(ReceiptFormat format);

    LegacyParsedReceipt parse(String[] pdfData, ReceiptFormat format);
}
