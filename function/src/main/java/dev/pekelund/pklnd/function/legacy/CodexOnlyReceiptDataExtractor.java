package dev.pekelund.pklnd.function.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.pklnd.function.ReceiptDataExtractor;
import dev.pekelund.pklnd.function.ReceiptExtractionResult;
import java.util.List;

/**
 * Receipt extractor that delegates to the Codex parser regardless of the detected format.
 */
public class CodexOnlyReceiptDataExtractor implements ReceiptDataExtractor {

    private final LegacyPdfReceiptExtractor delegate;

    public CodexOnlyReceiptDataExtractor(ObjectMapper objectMapper) {
        PdfParser pdfParser = new PdfParser(List.of(new CodexParser()), new ReceiptFormatDetector());
        this.delegate = new LegacyPdfReceiptExtractor(pdfParser, objectMapper);
    }

    @Override
    public ReceiptExtractionResult extract(byte[] pdfBytes, String fileName) {
        return delegate.extract(pdfBytes, fileName);
    }
}
