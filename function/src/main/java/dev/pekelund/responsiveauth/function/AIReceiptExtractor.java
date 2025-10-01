package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

/**
 * Invokes Gemini through Spring AI to extract structured data from receipt documents.
 */
public class AIReceiptExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AIReceiptExtractor.class);
    /**
     * Gemini text parts have an 8 KiB limit (8192 characters). We chunk the base64 payload
     * into 8000-character segments to stay comfortably below that ceiling while keeping the
     * chunks easy to process. Chunks are concatenated with newline separators; there is no
     * reassembly logic.
     */
    private static final int CHUNK_SIZE = 8_000;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final VertexAiGeminiChatOptions chatOptions;

    public AIReceiptExtractor(ChatModel chatModel, ObjectMapper objectMapper, VertexAiGeminiChatOptions chatOptions) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.chatOptions = chatOptions;
        LOGGER.info("constructing AIReceiptExtractor");
    }

    public ReceiptExtractionResult extract(byte[] pdfBytes, String fileName) {
        LOGGER.info("extract called with pdfBytes length: {}, fileName: {}", pdfBytes != null ? pdfBytes.length : null, fileName);
        boolean diagnosticFailureEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("GEMINI_DIAGNOSTIC_FAIL", "true"));
        if (diagnosticFailureEnabled) {
            LOGGER.error(
                "Diagnostic exception enabled via GEMINI_DIAGNOSTIC_FAIL environment variable - failing extract for file '{}' using model '{}'",
                fileName, chatOptions.getModel());
            throw new ReceiptParsingException("Diagnostic failure: AIReceiptExtractor extract invoked for file '" + fileName
                + "' using model '" + chatOptions.getModel() + "'" + " (GEMINI_DIAGNOSTIC_FAIL=true)");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            LOGGER.info("null och length == 0");
            throw new ReceiptParsingException("Cannot extract receipt data from an empty file");
        }

        String encoded = Base64.getEncoder().encodeToString(pdfBytes);
        String prompt = buildPrompt(encoded, fileName);

        LOGGER.info("AIReceiptExtractor invoking model '{}' with prompt length {} characters (base64 payload {} characters)",
            chatOptions.getModel(), prompt.length(), encoded.length());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("AIReceiptExtractor chat options instance id {} - {}", System.identityHashCode(chatOptions),
                chatOptions);
            LOGGER.debug("AIReceiptExtractor chat model implementation: {}", chatModel.getClass().getName());
        }

        Prompt request = new Prompt(new UserMessage(prompt), chatOptions);
        ChatResponse chatResponse = chatModel.call(request);
        if (chatResponse == null || chatResponse.getResult() == null) {
            throw new ReceiptParsingException("Gemini returned an empty response");
        }

        var generation = chatResponse.getResult();
        if (generation.getOutput() == null) {
            throw new ReceiptParsingException("Gemini returned an empty response");
        }

        String response = generation.getOutput().getText();
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
