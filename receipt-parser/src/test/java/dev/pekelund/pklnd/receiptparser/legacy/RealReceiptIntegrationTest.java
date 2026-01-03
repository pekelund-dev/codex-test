package dev.pekelund.pklnd.receiptparser.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.pklnd.receiptparser.ReceiptExtractionResult;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealReceiptIntegrationTest {

    private LegacyPdfReceiptExtractor extractor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        ReceiptFormatDetector detector = new ReceiptFormatDetector();
        PdfParser pdfParser = new PdfParser(
            List.of(new CodexParser(), new StandardFormatParser()),
            detector);
        extractor = new LegacyPdfReceiptExtractor(pdfParser, objectMapper);
    }

    @Test
    void parsesMalmborgsCaroliReceipt() throws IOException {
        parseAndVerify("ICA Kvantum Malmborgs Caroli 2025-12-03.pdf");
    }

    @Test
    void parsesMalmborgsCaroliReceipt_2025_02_05() throws IOException {
        parseAndVerify("ICA Kvantum Malmborgs Caroli 2025-02-05.pdf");
    }

    @Test
    void parsesMalmborgsErikslustReceipt_2025_09_08() throws IOException {
        parseAndVerify("ICA Kvantum Malmborgs Erikslust 2025-09-08.pdf");
    }

    @Test
    void parsesHansaReceipt_2025_04_02_3() throws IOException {
        parseAndVerify("ICA Supermarket Hansa 2025-04-02 (3).pdf");
    }

    @Test
    void parsesHansaReceipt_2025_10_18() throws IOException {
        parseAndVerify("ICA Supermarket Hansa 2025-10-18.pdf");
    }

    @Test
    void parsesHoorReceipt_2025_07_23() throws IOException {
        parseAndVerify("ICA Supermarket Höör 2025-07-23.pdf");
    }

    private void parseAndVerify(String filename) throws IOException {
        System.out.println("\n--- Testing: " + filename + " ---");
        try (InputStream is = getClass().getResourceAsStream("/" + filename)) {
            assertThat(is).as("Test resource not found: " + filename).isNotNull();
            byte[] pdfBytes = is.readAllBytes();

            ReceiptExtractionResult result = extractor.extract(pdfBytes, filename);

            assertThat(result.structuredData()).isNotNull();
            Map<String, Object> general = getMap(result.structuredData().get("general"));
            
            // Print the result to help debugging
            System.out.println("Parsed Format: " + general.get("format"));
            System.out.println("Total Amount: " + general.get("totalAmount"));
            System.out.println("Items found: " + getList(result.structuredData().get("items")).size());
            
            if (getList(result.structuredData().get("items")).isEmpty()) {
                 System.out.println("Raw Text:\n" + result.structuredData().get("rawText"));
            }

            // Basic assertions
            assertThat(general.get("totalAmount")).isNotNull();
            
            List<Map<String, Object>> items = getList(result.structuredData().get("items"));
            assertThat(items).isNotEmpty();
            
            items.forEach(item -> System.out.println("Item: " + item.get("name") + " | " + item.get("totalPrice")));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
