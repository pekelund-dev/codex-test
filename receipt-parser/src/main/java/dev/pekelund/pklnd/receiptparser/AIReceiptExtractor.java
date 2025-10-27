package dev.pekelund.pklnd.receiptparser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import dev.pekelund.pklnd.receiptparser.googleai.GeminiClient;
import dev.pekelund.pklnd.receiptparser.googleai.GoogleAiGeminiChatOptions;

/**
 * Invokes Gemini through the {@link GeminiClient} to extract structured data from receipt documents.
 */
public class AIReceiptExtractor implements ReceiptDataExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AIReceiptExtractor.class);
    /**
     * Gemini text parts have an 8 KiB limit (8192 characters). We chunk the base64 payload
     * into 8000-character segments to stay comfortably below that ceiling while keeping the
     * chunks easy to process. Chunks are concatenated with newline separators; there is no
     * reassembly logic.
     */
    private static final int CHUNK_SIZE = 8_000;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() { };
    private static final String FORMAT_INSTRUCTIONS = """
        Return a JSON object with the following structure:\n
        {\n
          \"general\": {\n
            \"storeName\": string|null,\n
            \"storeAddress\": string|null,\n
            \"receiptDate\": string|null (ISO-8601 date),\n
            \"totalAmount\": number|null,\n
            \"currency\": string|null,\n
            \"paymentMethod\": string|null,\n
            \"vatAmount\": number|null,\n
            \"otherFees\": string|null,\n
            \"metadata\": {\n
              \"receiptNumber\": string|null,\n
              \"cashier\": string|null,\n
              \"additionalNotes\": string|null\n
            }\n
          },\n
          \"items\": [\n
            {\n
              \"name\": string|null,\n
              \"category\": string|null,\n
              \"unitPrice\": number|null,\n
              \"quantity\": number|null,\n
              \"quantityUnit\": string|null,\n
              \"pricePerUnit\": number|null,\n
              \"discount\": number|null,\n
              \"totalPrice\": number|null\n
            }\n
          ],\n
          \"rawText\": string|null\n
        }\n
        Use null for unknown values and ensure numbers use a dot (.) as the decimal separator.\n
        Do not add code fences or commentary; return only the JSON document.
        """;

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final GoogleAiGeminiChatOptions chatOptions;

    public AIReceiptExtractor(GeminiClient geminiClient, ObjectMapper objectMapper, GoogleAiGeminiChatOptions chatOptions) {
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
        this.chatOptions = chatOptions;
        LOGGER.info("constructing AIReceiptExtractor");
    }

    @Override
    public ReceiptExtractionResult extract(byte[] pdfBytes, String fileName) {
        LOGGER.info("extract called with pdfBytes length: {}, fileName: {}", pdfBytes != null ? pdfBytes.length : null, fileName);
        if (pdfBytes == null || pdfBytes.length == 0) {
            LOGGER.info("null and length == 0");
            throw new ReceiptParsingException("Cannot extract receipt data from an empty file");
        }

        String encoded = Base64.getEncoder().encodeToString(pdfBytes);
        String prompt = buildPrompt(encoded, fileName);

        LOGGER.info("AIReceiptExtractor invoking model '{}' with prompt length {} characters (base64 payload {} characters)",
            chatOptions.getModel(), prompt.length(), encoded.length());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("AIReceiptExtractor chat options instance id {} - {}", System.identityHashCode(chatOptions),
                chatOptions);
            LOGGER.debug("AIReceiptExtractor Gemini client implementation: {}", geminiClient.getClass().getName());
        }

        String response = geminiClient.generateContent(prompt, chatOptions);
        if (!StringUtils.hasText(response)) {
            throw new ReceiptParsingException("Gemini returned an empty response");
        }

        LOGGER.info("Gemini raw response: {}", response);

        String sanitised = sanitiseResponse(response);
        if (!sanitised.equals(response)) {
            LOGGER.info("Sanitised Gemini response differs from raw output; sanitised value: {}", sanitised);
        }

        ReceiptStructuredOutput structuredOutput = parseStructuredResponse(sanitised);
        Map<String, Object> structuredData = objectMapper.convertValue(structuredOutput, MAP_TYPE);
        return new ReceiptExtractionResult(structuredData, response);
    }

    private String sanitiseResponse(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            LOGGER.info("Removing Markdown code fences from Gemini response");
            int firstBreak = trimmed.indexOf('\n');
            if (firstBreak > 0) {
                String language = trimmed.substring(3, firstBreak).trim();
                LOGGER.info("Gemini response declared fenced language '{}'", language);
                trimmed = trimmed.substring(firstBreak + 1, trimmed.length() - 3).trim();
            } else {
                trimmed = trimmed.substring(3, trimmed.length() - 3).trim();
            }
        }
        if (trimmed.startsWith("`") && trimmed.endsWith("`")) {
            LOGGER.info("Removing single backtick fences from Gemini response");
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (!trimmed.equals(response)) {
            LOGGER.info("Response trimmed from {} to {} characters during sanitisation", response.length(), trimmed.length());
        }
        return trimmed;
    }

    private String buildPrompt(String encodedPdf, String fileName) {
        String safeFileName = fileName != null ? fileName : "receipt.pdf";
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert system that extracts data from grocery receipts.\n");
        prompt.append("The document you will receive is a PDF receipt provided as base64 data between <receipt> tags.\n");
        prompt.append("Treat it as a receipt and extract the information using the following structured output instructions.\n");
        prompt.append(FORMAT_INSTRUCTIONS).append('\n');
        prompt.append("File name: ").append(safeFileName).append('\n');
        prompt.append("<receipt>\n");
        prompt.append(chunkText(encodedPdf));
        prompt.append("\n</receipt>");
        return prompt.toString();
    }

    private ReceiptStructuredOutput parseStructuredResponse(String response) {
        try {
            return objectMapper.readValue(response, ReceiptStructuredOutput.class);
        } catch (IOException ex) {
            LOGGER.error("Failed to parse Gemini response. Payload begins with: {}", preview(response));
            throw new ReceiptParsingException(
                "Gemini returned a response that could not be parsed into the expected structure",
                ex);
        }
    }

    private String preview(String response) {
        if (response == null) {
            return "<null>";
        }
        int max = Math.min(response.length(), 256);
        return response.substring(0, max);
    }

    private String chunkText(String encodedPdf) {
        if (encodedPdf.length() <= CHUNK_SIZE) {
            return encodedPdf;
        }
        // Using (encodedPdf.length() + CHUNK_SIZE - 1) / CHUNK_SIZE rounds up to ensure all
        // payload characters are included in CHUNK_SIZE (8000-character) segments.
        int numChunks = (encodedPdf.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        StringBuilder builder = new StringBuilder(encodedPdf.length() + numChunks);
        int index = 0;
        while (index < encodedPdf.length()) {
            int nextIndex = Math.min(index + CHUNK_SIZE, encodedPdf.length());
            builder.append(encodedPdf, index, nextIndex).append('\n');
            index = nextIndex;
        }
        return builder.toString();
    }
}

record ReceiptStructuredOutput(ReceiptGeneral general, List<ReceiptItem> items, String rawText) { }

record ReceiptGeneral(
    String storeName,
    String storeAddress,
    String receiptDate,
    BigDecimal totalAmount,
    String currency,
    String paymentMethod,
    BigDecimal vatAmount,
    String otherFees,
    ReceiptMetadata metadata
) { }

record ReceiptMetadata(String receiptNumber, String cashier, String additionalNotes) { }

record ReceiptItem(
    String name,
    String category,
    BigDecimal unitPrice,
    BigDecimal quantity,
    String quantityUnit,
    BigDecimal pricePerUnit,
    BigDecimal discount,
    BigDecimal totalPrice
) { }
