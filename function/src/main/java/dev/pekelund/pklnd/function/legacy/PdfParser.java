package dev.pekelund.pklnd.function.legacy;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PdfParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfParser.class);

    private final List<ReceiptFormatParser> formatParsers;
    private final ReceiptFormatDetector formatDetector;

    public PdfParser(List<ReceiptFormatParser> formatParsers, ReceiptFormatDetector formatDetector) {
        this.formatParsers = formatParsers;
        this.formatDetector = formatDetector;
    }

    LegacyParsedReceipt parse(String[] pdfData) {
        if (pdfData == null || pdfData.length == 0) {
            LOGGER.warn("Attempting to parse empty PDF data");
        } else {
            LOGGER.debug("Parsing PDF data with {} lines", pdfData.length);
            int sampleSize = Math.min(5, pdfData.length);
            LOGGER.debug("PDF data sample: {}", Arrays.toString(Arrays.copyOf(pdfData, sampleSize)));
        }

        ReceiptFormat format = formatDetector.detectFormat(pdfData);
        LOGGER.debug("Detected receipt format: {}", format);

        for (ReceiptFormatParser parser : formatParsers) {
            if (parser.supportsFormat(format)) {
                return parser.parse(pdfData, format);
            }
        }

        LOGGER.warn("No parser available for format {}", format);
        LegacyReceiptError error = new LegacyReceiptError(-1, null,
            format == ReceiptFormat.UNKNOWN
                ? "Unable to determine receipt format"
                : "No parser registered for format " + format);
        return new LegacyParsedReceipt(format, null, null, null, List.of(), List.of(), List.of(), List.of(error));
    }
}
