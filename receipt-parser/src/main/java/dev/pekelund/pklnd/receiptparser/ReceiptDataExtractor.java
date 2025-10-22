package dev.pekelund.pklnd.receiptparser;

public interface ReceiptDataExtractor {

    ReceiptExtractionResult extract(byte[] pdfBytes, String fileName);
}
