package dev.pekelund.pklnd.utils.pdf;

import dev.pekelund.pklnd.models.receipts.Receipt;
import dev.pekelund.pklnd.models.errors.Error;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Component
public class PdfParser {
    private static final Logger logger = LoggerFactory.getLogger(PdfParser.class);

    private final List<ReceiptFormatParser> formatParsers;
    private final ReceiptFormatDetector formatDetector;

    public PdfParser(List<ReceiptFormatParser> formatParsers, ReceiptFormatDetector formatDetector) {
        this.formatParsers = formatParsers;
        this.formatDetector = formatDetector;
    }

    public Receipt parsePdfData(String[] pdfData, String userId, URL url) {
        // Log the received pdfData
        logger.debug("Received PDF data: {}", Arrays.toString(pdfData));
        logger.debug("Parsing PDF data...");

        // Detect the format of the receipt
        ReceiptFormat format = formatDetector.detectFormat(pdfData);
        logger.info("Detected receipt format: {}", format);

        // Find the appropriate parser for this format
        for (ReceiptFormatParser parser : formatParsers) {
            if (parser.supportsFormat(format)) {
                return parser.parse(pdfData, userId, url);
            }
        }

        // If no parser is found, log an error and return an empty receipt
        logger.error("No parser found for format: {}", format);
        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt.setUrl(url);
        receipt.addError(new Error(null, 0, "Unsupported receipt format", receipt));
        return receipt;
    }
}