package dev.pekelund.responsiveauth.function.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.responsiveauth.function.ReceiptExtractionResult;
import dev.pekelund.responsiveauth.function.ReceiptParsingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyPdfReceiptExtractorTest {

    private LegacyPdfReceiptExtractor extractor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        ReceiptFormatDetector detector = new ReceiptFormatDetector();
        PdfParser pdfParser = new PdfParser(
            List.of(new StandardFormatParser(), new CodexParser(), new NewFormatParser()),
            detector);
        extractor = new LegacyPdfReceiptExtractor(pdfParser, objectMapper);
    }

    @Test
    void parsesStandardReceiptPdf() throws IOException {
        byte[] pdfBytes = createPdf("""
            ICA KVANTUM
            ICA Kvantum Testbutik
            2024-09-30 12:34 AID:123456
            Kvittonr: 123456789
            Beskrivning Art. nr. Pris Mängd Summa(SEK)
            Banan 7318690081055 10,00 1 st 10,00
            Äpple 7318690081062 5,50 2 st 11,00
            Moms % Moms Netto Brutto
            25 4,20 16,80 21,00
            Total 21,00
            """);

        ReceiptExtractionResult result = extractor.extract(pdfBytes, "sample.pdf");

        assertThat(result.structuredData()).isNotNull();
        Map<String, Object> general = getMap(result.structuredData().get("general"));
        assertThat(general.get("format")).isEqualTo("STANDARD");
        assertThat(new BigDecimal(general.get("totalAmount").toString()))
            .isEqualByComparingTo(new BigDecimal("21.00"));
        assertThat(general.get("fileName")).isEqualTo("sample.pdf");

        List<Map<String, Object>> items = getList(result.structuredData().get("items"));
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsEntry("name", "Banan");
        assertThat(items.get(0)).containsEntry("unitPrice", new BigDecimal("10.00"));
        assertThat(items.get(1)).containsEntry("name", "Äpple");
        assertThat(items.get(1)).containsEntry("unitPrice", new BigDecimal("5.50"));

        List<Map<String, Object>> vats = getList(result.structuredData().get("vats"));
        assertThat(vats).hasSize(1);
        assertThat(vats.get(0)).containsEntry("rate", new BigDecimal("25"));
        assertThat(vats.get(0)).containsEntry("taxAmount", new BigDecimal("4.20"));

        String rawText = (String) result.structuredData().get("rawText");
        assertThat(rawText).contains("Beskrivning Art. nr. Pris Mängd Summa(SEK)");
    }

    @Test
    void rejectsEmptyPdf() {
        assertThatThrownBy(() -> extractor.extract(new byte[0], "empty.pdf"))
            .isInstanceOf(ReceiptParsingException.class)
            .hasMessageContaining("empty PDF");
    }

    private byte[] createPdf(String content) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.setLeading(14.5f);
                stream.beginText();
                stream.newLineAtOffset(50, 750);
                for (String line : content.strip().split("\n")) {
                    stream.showText(line.strip());
                    stream.newLine();
                }
                stream.endText();
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.save(output);
                return output.toByteArray();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Map<String, Object>>) value;
    }
}
