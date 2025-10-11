package dev.pekelund.pklnd.function.legacy;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReceiptFormatDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptFormatDetector.class);

    public ReceiptFormat detectFormat(String[] pdfData) {
        if (pdfData == null || pdfData.length == 0) {
            LOGGER.warn("Empty receipt data while detecting format");
            return ReceiptFormat.UNKNOWN;
        }

        LOGGER.debug("Detecting receipt format for PDF data with {} lines", pdfData.length);

        int sampleSize = Math.min(5, pdfData.length);
        LOGGER.debug("Receipt header sample: {}", Arrays.toString(Arrays.copyOf(pdfData, sampleSize)));

        for (String line : pdfData) {
            if (line.contains("Kvittonr:")) {
                LOGGER.debug("Detected STANDARD format via 'Kvittonr:' marker");
                return ReceiptFormat.STANDARD;
            }
        }

        for (String line : pdfData) {
            if (line.contains("Kvitto nr")) {
                LOGGER.debug("Detected NEW_FORMAT via 'Kvitto nr' marker");
                return ReceiptFormat.NEW_FORMAT;
            }
        }

        boolean hasStandardHeaders = Arrays.stream(pdfData)
            .anyMatch(line -> line.contains("Beskrivning Art. nr. Pris Mängd Summa(SEK)"));
        if (hasStandardHeaders) {
            LOGGER.debug("Detected STANDARD format via header pattern");
            return ReceiptFormat.STANDARD;
        }

        boolean hasNewFormatHeaders = Arrays.stream(pdfData)
            .anyMatch(line -> line.contains("Artikellista") || line.contains("Totalt att betala"));
        if (hasNewFormatHeaders) {
            LOGGER.debug("Detected NEW_FORMAT via fallback pattern");
            return ReceiptFormat.NEW_FORMAT;
        }

        LOGGER.warn("Unable to determine receipt format");
        return ReceiptFormat.UNKNOWN;
    }
}
