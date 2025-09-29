package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Invokes Gemini through Spring AI to extract structured data from receipt documents.
 */
public class GeminiReceiptExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiReceiptExtractor.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public GeminiReceiptExtractor(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public ReceiptExtractionResult extract(byte[] pdfBytes, String fileName) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ReceiptParsingException("Cannot extract receipt data from an empty file");
        }

        String encoded = Base64.getEncoder().encodeToString(pdfBytes);
        String prompt = buildPrompt(encoded, fileName);

        String response = chatModel.call(prompt);
        if (response == null) {
            throw new ReceiptParsingException("Gemini returned an empty response");
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() { });
            return new ReceiptExtractionResult(parsed, response);
        } catch (JsonProcessingException ex) {
            LOGGER.debug("Gemini response could not be parsed as JSON: {}", response);
            throw new ReceiptParsingException("Gemini returned a non-JSON response", ex);
        }
    }

    private String buildPrompt(String encodedPdf, String fileName) {
        String safeFileName = fileName != null ? fileName : "receipt.pdf";
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert system that extracts data from grocery receipts.\n");
        prompt.append("The document you will receive is a PDF receipt provided as base64 data between <receipt> tags.\n");
        prompt.append("Treat it as a receipt and extract the information into JSON with the following structure:\n");
        prompt.append("{\n");
        prompt.append("  \"general\": {\n");
        prompt.append("    \"storeName\": string,\n");
        prompt.append("    \"storeAddress\": string,\n");
        prompt.append("    \"receiptDate\": string (ISO-8601 date or date-time),\n");
        prompt.append("    \"totalAmount\": number,\n");
        prompt.append("    \"currency\": string,\n");
        prompt.append("    \"paymentMethod\": string,\n");
        prompt.append("    \"vatAmount\": number,\n");
        prompt.append("    \"otherFees\": string,\n");
        prompt.append("    \"metadata\": {\n");
        prompt.append("      \"receiptNumber\": string,\n");
        prompt.append("      \"cashier\": string,\n");
        prompt.append("      \"additionalNotes\": string\n");
        prompt.append("    }\n");
        prompt.append("  },\n");
        prompt.append("  \"items\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": string,\n");
        prompt.append("      \"category\": string,\n");
        prompt.append("      \"unitPrice\": number,\n");
        prompt.append("      \"quantity\": number,\n");
        prompt.append("      \"quantityUnit\": string,\n");
        prompt.append("      \"pricePerUnit\": number,\n");
        prompt.append("      \"discount\": number,\n");
        prompt.append("      \"totalPrice\": number\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"rawText\": string\n");
        prompt.append("}\n");
        prompt.append("If data is missing, use null values. Always return only the JSON document.\n");
        prompt.append("File name: ").append(safeFileName).append('\n');
        prompt.append("<receipt>\n");
        prompt.append(chunkText(encodedPdf));
        prompt.append("\n</receipt>");
        return prompt.toString();
    }

    private String chunkText(String encodedPdf) {
        if (encodedPdf.length() <= 8000) {
            return encodedPdf;
        }
        int numChunks = (encodedPdf.length() + 7999) / 8000;
        StringBuilder builder = new StringBuilder(encodedPdf.length() + numChunks);
        int index = 0;
        while (index < encodedPdf.length()) {
            int nextIndex = Math.min(index + 8000, encodedPdf.length());
            builder.append(encodedPdf, index, nextIndex).append('\n');
            index = nextIndex;
        }
        return builder.toString();
    }
}
