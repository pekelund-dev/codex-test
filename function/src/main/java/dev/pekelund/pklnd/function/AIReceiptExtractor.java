package dev.pekelund.pklnd.function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

/**
 * Invokes Gemini through Spring AI to extract structured data from receipt documents.
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

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final VertexAiGeminiChatOptions chatOptions;
    private final BeanOutputConverter<ReceiptStructuredOutput> receiptOutputConverter;

    public AIReceiptExtractor(ChatModel chatModel, ObjectMapper objectMapper, VertexAiGeminiChatOptions chatOptions) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.chatOptions = chatOptions;
        this.receiptOutputConverter = new BeanOutputConverter<>(ReceiptStructuredOutput.class, objectMapper);
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
        prompt.append(receiptOutputConverter.getFormat()).append('\n');
        prompt.append("If data is missing, use null values. Always return only the JSON document.\n");
        prompt.append("Do not include code fences, explanations, or any text outside the JSON object beyond what the format instructions specify.\n");
        prompt.append("File name: ").append(safeFileName).append('\n');
        prompt.append("<receipt>\n");
        prompt.append(chunkText(encodedPdf));
        prompt.append("\n</receipt>");
        return prompt.toString();
    }

    private ReceiptStructuredOutput parseStructuredResponse(String response) {
        try {
            LOGGER.info("Parsing Gemini response with Spring AI output converter {}", receiptOutputConverter.getClass().getName());
            return receiptOutputConverter.convert(response);
        } catch (RuntimeException ex) {
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
