package dev.pekelund.responsiveauth.function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import dev.pekelund.responsiveauth.function.legacy.LegacyPdfReceiptExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HybridReceiptExtractorTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void usesLegacyResultWhenUsable() {
        LegacyPdfReceiptExtractor legacyExtractor = mock(LegacyPdfReceiptExtractor.class);
        AIReceiptExtractor aiExtractor = mock(AIReceiptExtractor.class);
        ReceiptExtractionResult legacyResult = new ReceiptExtractionResult(
            Map.of(
                "general", Map.of(
                    "format", "STANDARD",
                    "totalAmount", new BigDecimal("19.98")),
                "items", List.of(Map.of("name", "Milk"))),
            "{\"general\":{}}"
        );
        doReturn(legacyResult).when(legacyExtractor).extract(any(), eq("sample.pdf"));

        HybridReceiptExtractor extractor = new HybridReceiptExtractor(legacyExtractor, aiExtractor, objectMapper);
        ReceiptExtractionResult result = extractor.extract(new byte[] {1, 2, 3}, "sample.pdf");

        assertThat(result).isSameAs(legacyResult);
        verify(aiExtractor, never()).extract(any(), any());
    }

    @Test
    void fallsBackToAiWhenLegacyNotUsable() {
        LegacyPdfReceiptExtractor legacyExtractor = mock(LegacyPdfReceiptExtractor.class);
        AIReceiptExtractor aiExtractor = mock(AIReceiptExtractor.class);

        ReceiptExtractionResult legacyResult = new ReceiptExtractionResult(
            Map.of(
                "general", Map.of("format", "UNKNOWN"),
                "items", List.of()),
            "{\"general\":{}}"
        );
        Map<String, Object> aiData = Map.of(
            "general", Map.of("source", "ai"),
            "items", List.of(Map.of("name", "Fallback"))
        );
        ReceiptExtractionResult aiResult = new ReceiptExtractionResult(aiData, "{\"general\":{\"source\":\"ai\"}}");

        doReturn(legacyResult).when(legacyExtractor).extract(any(), eq("sample.pdf"));
        doReturn(aiResult).when(aiExtractor).extract(any(), eq("sample.pdf"));

        HybridReceiptExtractor extractor = new HybridReceiptExtractor(legacyExtractor, aiExtractor, objectMapper);
        ReceiptExtractionResult result = extractor.extract(new byte[] {4, 5, 6}, "sample.pdf");

        assertThat(result.structuredData()).containsEntry("source", "hybrid");
        assertThat(result.structuredData()).containsEntry("primary", "gemini");
        assertThat(result.structuredData()).containsEntry("legacy", legacyResult.structuredData());
        assertThat(result.structuredData()).containsEntry("gemini", aiResult.structuredData());
        assertThat(result.structuredData()).containsEntry("general", aiData.get("general"));
        assertThat(result.structuredData()).containsEntry("items", aiData.get("items"));
        verify(aiExtractor).extract(any(), eq("sample.pdf"));
    }

    @Test
    void propagatesAiFailuresWhenLegacyAlsoFails() {
        LegacyPdfReceiptExtractor legacyExtractor = mock(LegacyPdfReceiptExtractor.class);
        AIReceiptExtractor aiExtractor = mock(AIReceiptExtractor.class);

        doThrow(new ReceiptParsingException("legacy boom"))
            .when(legacyExtractor).extract(any(), eq("sample.pdf"));
        doThrow(new ReceiptParsingException("ai boom"))
            .when(aiExtractor).extract(any(), eq("sample.pdf"));

        HybridReceiptExtractor extractor = new HybridReceiptExtractor(legacyExtractor, aiExtractor, objectMapper);

        assertThatThrownBy(() -> extractor.extract(new byte[] {7, 8, 9}, "sample.pdf"))
            .isInstanceOf(ReceiptParsingException.class)
            .hasMessageContaining("ai boom");
        verify(aiExtractor).extract(any(), eq("sample.pdf"));
    }
}
