package dev.pekelund.pklnd.utils.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.pekelund.pklnd.models.receipts.Receipt;
import java.io.File;
import java.io.IOException;
import java.net.URL;

public abstract class BaseReceiptParser implements ReceiptFormatParser {
    private static final Logger logger = LoggerFactory.getLogger(BaseReceiptParser.class);

    /**
     * Reads the content of a PDF file and returns it as an array of lines.
     *
     * @param pdfFile The PDF file to read.
     * @return An array of strings representing the lines in the PDF.
     * @throws IOException If an error occurs while reading the PDF.
     */
    protected String[] readPdf(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text.split("\\r?\\n");
        }
    }

    /**
     * Reads the content of a PDF from a URL and returns it as an array of lines.
     *
     * @param pdfUrl The URL of the PDF to read.
     * @return An array of strings representing the lines in the PDF.
     * @throws IOException If an error occurs while reading the PDF.
     */
    protected String[] readPdf(URL pdfUrl) throws IOException {
        File tempFile = File.createTempFile("receipt", ".pdf");
        try {
            org.apache.commons.io.FileUtils.copyURLToFile(pdfUrl, tempFile);
            return readPdf(tempFile);
        } finally {
            if (!tempFile.delete()) {
                logger.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    /**
     * Detects the format of the receipt based on its content.
     *
     * @param pdfData The lines of text extracted from the PDF.
     * @param formatDetector The format detector to use.
     * @return The detected receipt format.
     */
    protected ReceiptFormat detectFormat(String[] pdfData, ReceiptFormatDetector formatDetector) {
        ReceiptFormat format = formatDetector.detectFormat(pdfData);
        logger.info("Detected receipt format: {}", format);
        return format;
    }

    /**
     * Abstract method to parse the receipt data.
     * Subclasses must implement this method to handle specific formats.
     *
     * @param pdfData The lines of text extracted from the PDF.
     * @param userId The user ID associated with the receipt.
     * @param url The URL of the receipt PDF.
     * @return The parsed receipt.
     */
    public abstract Receipt parse(String[] pdfData, String userId, URL url);
}
