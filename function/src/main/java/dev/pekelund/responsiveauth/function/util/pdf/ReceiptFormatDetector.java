package dev.pekelund.pklnd.utils.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ReceiptFormatDetector {
    private static final Logger logger = LoggerFactory.getLogger(ReceiptFormatDetector.class);

    /**
     * Detects the format of a receipt based on its content
     */
    public ReceiptFormat detectFormat(String[] pdfData) {
        if (pdfData == null || pdfData.length == 0) {
            logger.warn("Empty receipt data");
            return ReceiptFormat.UNKNOWN;
        }

        // Log sample of the receipt content
        if (logger.isDebugEnabled()) {
            int sampleSize = Math.min(5, pdfData.length);
            logger.debug("Receipt header sample: {}", 
                Arrays.toString(Arrays.copyOfRange(pdfData, 0, sampleSize)));
        }

        // Format 1 detection: "Kvittonr:"
        for (String line : pdfData) {
            if (line.contains("Kvittonr:")) {
                logger.debug("Detected Format 1 (STANDARD) by 'Kvittonr:' pattern");
                return ReceiptFormat.STANDARD;
            }
        }

        // Format 2 detection: "Kvitto nr"
        for (String line : pdfData) {
            if (line.contains("Kvitto nr")) {
                logger.debug("Detected Format 2 (NEW_FORMAT) by 'Kvitto nr' pattern");
                return ReceiptFormat.NEW_FORMAT;
            }
        }

        // Fall back to additional checks if the primary patterns aren't found
        return fallbackDetection(pdfData);
    }

    /**
     * Provides additional checks if primary patterns aren't found
     */
    private ReceiptFormat fallbackDetection(String[] pdfData) {
        // Look for format 1 standard headers
        boolean hasFormat1Headers = Arrays.stream(pdfData)
            .anyMatch(line -> line.contains("Beskrivning Art. nr. Pris Mängd Summa(SEK)"));
            
        if (hasFormat1Headers) {
            logger.debug("Detected Format 1 (STANDARD) by header pattern");
            return ReceiptFormat.STANDARD;
        }

        // Look for format 2 specific patterns
        boolean hasFormat2Pattern = Arrays.stream(pdfData)
            .anyMatch(line -> line.contains("Artikellista") || line.contains("Totalt att betala"));
            
        if (hasFormat2Pattern) {
            logger.debug("Detected Format 2 (NEW_FORMAT) by secondary patterns");
            return ReceiptFormat.NEW_FORMAT;
        }

        logger.warn("Could not determine receipt format");
        return ReceiptFormat.UNKNOWN;
    }
}
