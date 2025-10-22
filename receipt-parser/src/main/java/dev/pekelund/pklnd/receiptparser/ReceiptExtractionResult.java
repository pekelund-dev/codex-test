package dev.pekelund.pklnd.receiptparser;

import java.util.Map;

/**
 * Structured response returned from Gemini after parsing a receipt.
 *
 * @param structuredData parsed JSON structure representing the receipt
 * @param rawResponse    unmodified response returned by Gemini
 */
public record ReceiptExtractionResult(Map<String, Object> structuredData, String rawResponse) {
}
