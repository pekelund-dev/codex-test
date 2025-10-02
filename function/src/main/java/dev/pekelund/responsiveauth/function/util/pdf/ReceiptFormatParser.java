package dev.pekelund.pklnd.utils.pdf;

import dev.pekelund.pklnd.models.receipts.Receipt;

import java.net.URL;

public interface ReceiptFormatParser {
    /**
     * Check if this parser supports the specified receipt format
     */
    boolean supportsFormat(ReceiptFormat format);

    /**
     * Parse receipt data in the supported format
     */
    Receipt parse(String[] pdfData, String userId, URL url);
}
