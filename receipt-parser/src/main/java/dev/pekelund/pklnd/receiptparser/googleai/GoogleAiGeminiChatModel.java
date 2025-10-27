package dev.pekelund.pklnd.receiptparser.googleai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Simple {@link ChatModel} implementation that invokes Google AI Studio's Gemini API using
 * an API key.
 */
public class GoogleAiGeminiChatModel implements ChatModel {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleAiGeminiChatModel.class);

    private final RestClient restClient;
    private final String apiKey;
    private final GoogleAiGeminiChatOptions defaultOptions;
    private final ObservationRegistry observationRegistry;

    public GoogleAiGeminiChatModel(RestClient restClient, String apiKey, GoogleAiGeminiChatOptions defaultOptions,
        ObservationRegistry observationRegistry) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.defaultOptions = defaultOptions != null ? defaultOptions : GoogleAiGeminiChatOptions.builder().build();
        this.observationRegistry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Assert.notNull(prompt, "Prompt must not be null");
        GoogleAiGeminiChatOptions resolvedOptions = resolveOptions(prompt.getOptions());
        String promptText = extractPromptText(prompt.getInstructions());
        Observation observation = Observation.start("google.ai.gemini.call", observationRegistry)
            .highCardinalityKeyValue("model", Optional.ofNullable(resolvedOptions.getModel()).orElse("(unset)"));
        try (Observation.Scope scope = observation.openScope()) {
            LOGGER.info("Calling Google AI Gemini model '{}' with prompt length {}", resolvedOptions.getModel(),
                promptText.length());
            GenerateContentRequest request = buildRequest(promptText, resolvedOptions);
            GenerateContentResponse response = executeRequest(resolvedOptions.getModel(), request);
            String content = extractContent(response);
            AssistantMessage assistantMessage = new AssistantMessage(content);
            return new ChatResponse(List.of(new Generation(assistantMessage)));
        } catch (RuntimeException ex) {
            observation.error(ex);
            throw ex;
        } finally {
            observation.stop();
        }
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }

    private GoogleAiGeminiChatOptions resolveOptions(ChatOptions promptOptions) {
        if (promptOptions instanceof GoogleAiGeminiChatOptions googleOptions) {
            return defaultOptions.merge(googleOptions);
        }
        return defaultOptions;
    }

    private String extractPromptText(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            throw new IllegalArgumentException("Prompt must contain at least one message");
        }
        return messages.stream()
            .map(Message::getText)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));
    }

    private GenerateContentRequest buildRequest(String promptText, GoogleAiGeminiChatOptions options) {
        GenerateContentRequest.Content content = new GenerateContentRequest.Content("user",
            List.of(new GenerateContentRequest.Part(promptText)));
        GenerateContentRequest.GenerationConfig generationConfig = new GenerateContentRequest.GenerationConfig(
            options.getTemperature(), options.getTopP(), options.getTopK(), options.getMaxOutputTokens());
        return new GenerateContentRequest(List.of(content), generationConfig);
    }

    private GenerateContentResponse executeRequest(String model, GenerateContentRequest request) {
        String modelName = StringUtils.hasText(model) ? model : defaultOptions.getModel();
        if (!StringUtils.hasText(modelName)) {
            throw new IllegalStateException("Gemini model name must be configured");
        }
        try {
            return restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/models/{model}:generateContent")
                    .queryParam("key", apiKey)
                    .build(modelName))
                .body(request)
                .retrieve()
                .body(GenerateContentResponse.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Google AI Gemini request failed", ex);
        }
    }

    private String extractContent(GenerateContentResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.candidates())) {
            throw new IllegalStateException("Gemini response did not contain any candidates");
        }
        return response.candidates().stream()
            .filter(candidate -> candidate != null && candidate.content() != null)
            .flatMap(candidate -> {
                List<GenerateContentResponse.Part> parts = candidate.content().parts();
                return parts != null ? parts.stream() : List.<GenerateContentResponse.Part>of().stream();
            })
            .map(GenerateContentResponse.Part::text)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Gemini response did not contain any text parts"));
    }

    private record GenerateContentRequest(List<Content> contents, GenerationConfig generationConfig) {

        private record Content(String role, List<Part> parts) {
        }

        private record Part(String text) {
        }

        private record GenerationConfig(Double temperature, Double topP, Integer topK, Integer maxOutputTokens) {
        }
    }

    private record GenerateContentResponse(List<Candidate> candidates) {

        private record Candidate(Content content) {
        }

        private record Content(List<Part> parts) {
        }

        private record Part(String text) {
        }
    }
}

