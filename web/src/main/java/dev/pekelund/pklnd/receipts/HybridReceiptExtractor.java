package dev.pekelund.pklnd.receipts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.pklnd.receipts.legacy.LegacyPdfReceiptExtractor;
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
        LOGGER.info("Hybrid extractor starting for file {}", fileName);
        ReceiptExtractionResult legacyResult = null;
        try {
            legacyResult = legacyExtractor.extract(pdfBytes, fileName);
            LOGGER.info("Legacy parser returned structured data keys: {}", legacyResult != null
                && legacyResult.structuredData() != null ? legacyResult.structuredData().keySet() : null);
            if (isUsable(legacyResult, fileName)) {
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
        Map<String, Object> primaryData = aiResult.structuredData();
        if (primaryData != null && !primaryData.isEmpty()) {
            combined.putAll(primaryData);
        }

        combined.put("source", "hybrid");
        combined.put("primary", "gemini");
        combined.put("legacy", legacyResult.structuredData());
        combined.put("gemini", primaryData);
        String rawResponse = toJson(combined, legacyResult.rawResponse(), aiResult.rawResponse());
        return new ReceiptExtractionResult(combined, rawResponse);
    }

    private boolean isUsable(ReceiptExtractionResult result, String fileName) {
        if (result == null || result.structuredData() == null) {
            LOGGER.info("Legacy parser unusable for file {} because result or structured data was null", fileName);
            return false;
        }
        Object general = result.structuredData().get("general");
        boolean formatKnown = false;
        if (general instanceof Map<?, ?> generalMap) {
            Object format = generalMap.get("format");
            if (format instanceof String formatString) {
                formatKnown = !"UNKNOWN".equalsIgnoreCase(formatString);
                LOGGER.info("Legacy parser format for file {} is '{}' (known = {})", fileName, formatString, formatKnown);
            }
            Object totalAmount = generalMap.get("totalAmount");
            if (totalAmount != null) {
                formatKnown = true;
                LOGGER.info("Legacy parser detected total amount {} for file {}", totalAmount, fileName);
            }
        } else {
            LOGGER.info("Legacy parser general section missing or not a map for file {}", fileName);
        }

        Object items = result.structuredData().get("items");
        boolean hasItems = items instanceof List<?> list && !list.isEmpty();
        if (!hasItems) {
            LOGGER.info("Legacy parser produced no items for file {}", fileName);
        } else {
            LOGGER.info("Legacy parser produced {} items for file {}", ((List<?>) items).size(), fileName);
        }
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
