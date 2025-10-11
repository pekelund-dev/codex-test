package dev.pekelund.pklnd.function;

public interface ReceiptDataExtractor {

    ReceiptExtractionResult extract(byte[] pdfBytes, String fileName);
}
