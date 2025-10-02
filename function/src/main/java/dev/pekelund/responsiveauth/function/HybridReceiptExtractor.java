package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.responsiveauth.function.legacy.LegacyPdfReceiptExtractor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HybridReceiptExtractor implements ReceiptDataExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HybridReceiptExtractor.class);

    private final LegacyPdfReceiptExtractor legacyExtractor;
    private final AIReceiptExtractor aiReceiptExtractor;
    private final ObjectMapper objectMapper;

    public HybridReceiptExtractor(LegacyPdfReceiptExtractor legacyExtractor, AIReceiptExtractor aiReceiptExtractor,
        ObjectMapper objectMapper) {
        this.legacyExtractor = legacyExtractor;
        this.aiReceiptExtractor = aiReceiptExtractor;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReceiptExtractionResult extract(byte[] pdfBytes, String fileName) {
        ReceiptExtractionResult legacyResult = null;
        try {
            legacyResult = legacyExtractor.extract(pdfBytes, fileName);
            if (isUsable(legacyResult)) {
                LOGGER.info("Using legacy PDF parser result for file {}", fileName);
                return legacyResult;
            }
            LOGGER.info("Legacy parser result for file {} lacked required data; falling back to Gemini", fileName);
        } catch (ReceiptParsingException ex) {
            LOGGER.warn("Legacy parser failed for file {} - {}", fileName, ex.getMessage());
            legacyResult = null;
        }

        ReceiptExtractionResult aiResult = aiReceiptExtractor.extract(pdfBytes, fileName);
        if (legacyResult == null) {
            return aiResult;
        }

        Map<String, Object> combined = new LinkedHashMap<>();
        combined.put("source", "hybrid");
        combined.put("primary", "gemini");
        combined.put("legacy", legacyResult.structuredData());
        combined.put("gemini", aiResult.structuredData());
        String rawResponse = toJson(combined, legacyResult.rawResponse(), aiResult.rawResponse());
        return new ReceiptExtractionResult(combined, rawResponse);
    }

    private boolean isUsable(ReceiptExtractionResult result) {
        if (result == null || result.structuredData() == null) {
            return false;
        }
        Object general = result.structuredData().get("general");
        boolean formatKnown = false;
        if (general instanceof Map<?, ?> generalMap) {
            Object format = generalMap.get("format");
            if (format instanceof String formatString) {
                formatKnown = !"UNKNOWN".equalsIgnoreCase(formatString);
            }
            Object totalAmount = generalMap.get("totalAmount");
            if (totalAmount != null) {
                formatKnown = true;
            }
        }

        Object items = result.structuredData().get("items");
        boolean hasItems = items instanceof List<?> list && !list.isEmpty();
        return formatKnown && hasItems;
    }

    private String toJson(Map<String, Object> combined, String legacyRaw, String aiRaw) {
        Map<String, Object> payload = new LinkedHashMap<>(combined);
        payload.put("legacyRaw", legacyRaw);
        payload.put("geminiRaw", aiRaw);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Failed to serialise hybrid extraction payload", ex);
            return payload.toString();
        }
    }
}
