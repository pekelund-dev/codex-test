package dev.pekelund.pklnd.utils.pdf;

import java.io.IOException;
import java.net.URL;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PdfFetcher {
    private static final Logger logger = LoggerFactory.getLogger(PdfFetcher.class);

    public String[] fetchPdfData(URL pdfUrl) throws IOException {
        try (PDDocument document = PDDocument.load(pdfUrl.openStream())) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);
            return text.split("\\r?\\n");
        } catch (IOException e) {
            logger.error("Error fetching PDF data", e);
            throw e;
        }
    }
}