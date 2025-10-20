package dev.pekelund.pklnd.receipts;

public interface ReceiptDataExtractor {

    ReceiptExtractionResult extract(byte[] pdfBytes, String fileName);
}
