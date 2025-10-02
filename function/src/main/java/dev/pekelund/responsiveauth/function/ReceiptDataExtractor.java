package dev.pekelund.responsiveauth.function;

public interface ReceiptDataExtractor {

    ReceiptExtractionResult extract(byte[] pdfBytes, String fileName);
}
