package dev.pekelund.pklnd.receiptparser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

/**
 * Minimal {@link ChatModel} implementation that uses the Google AI Studio REST
 * API via an API key. This keeps the receipt parser within the Spring AI
 * abstraction while avoiding Vertex AI permissions when only a Studio key is
 * available.
 */
public class GoogleAiStudioChatModel implements ChatModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleAiStudioChatModel.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String defaultModel;
    private final ChatOptions defaultOptions;

    public GoogleAiStudioChatModel(String apiKey, String defaultModel, RestClient restClient) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("Google AI Studio API key must be provided");
        }
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.defaultOptions = ChatOptions.builder().model(defaultModel).build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatOptions promptOptions = prompt.getOptions();
        ChatOptions effectiveOptions = promptOptions != null ? promptOptions : defaultOptions;
        String modelName = resolveModelName(effectiveOptions);

        GoogleAiRequest request = buildRequest(prompt);
        LOGGER.info("Google AI Studio invoking model '{}' with {} content block(s)", modelName, request.contents().size());

        GoogleAiResponse response;
        try {
            response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1beta/models/{model}:generateContent")
                    .queryParam("key", apiKey)
                    .build(modelName))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GoogleAiResponse.class);
        } catch (RestClientResponseException ex) {
            LOGGER.error("Google AI Studio call failed with status {} and body {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("Failed to generate content", ex);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate content", ex);
        }

        if (response == null || CollectionUtils.isEmpty(response.candidates())) {
            throw new RuntimeException("Gemini returned an empty response");
        }

        String text = response.firstCandidateText();
        if (!StringUtils.hasText(text)) {
            LOGGER.error("Google AI Studio returned an empty candidate. Prompt feedback: {}", response.promptFeedback());
            throw new RuntimeException("Gemini returned an empty response");
        }

        LOGGER.info("Google AI Studio raw response: {}", text);
        AssistantMessage assistantMessage = new AssistantMessage(text, Map.of());
        Generation generation = new Generation(assistantMessage, null);
        return new ChatResponse(Collections.singletonList(generation));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.error(new UnsupportedOperationException("Google AI Studio streaming is not supported"));
    }

    private GoogleAiRequest buildRequest(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        List<GoogleAiContent> contents = new ArrayList<>();
        if (!CollectionUtils.isEmpty(messages)) {
            for (Message message : messages) {
                contents.add(convertMessage(message));
            }
        } else {
            String contentsText = prompt.getContents();
            contents.add(GoogleAiContent.user(contentsText));
        }
        return new GoogleAiRequest(contents);
    }

    private GoogleAiContent convertMessage(Message message) {
        if (message instanceof UserMessage userMessage) {
            return GoogleAiContent.user(userMessage.getText());
        }
        if (message instanceof SystemMessage systemMessage) {
            return GoogleAiContent.system(systemMessage.getText());
        }
        return GoogleAiContent.user(message.getText());
    }

    private String resolveModelName(ChatOptions chatOptions) {
        if (chatOptions == null) {
            return defaultModel;
        }
        String model = chatOptions.getModel();
        if (StringUtils.hasText(model)) {
            return model;
        }
        return defaultModel;
    }

    record GoogleAiRequest(List<GoogleAiContent> contents) { }

    record GoogleAiContent(String role, List<GoogleAiPart> parts) {

        static GoogleAiContent user(String text) {
            return new GoogleAiContent("user", List.of(GoogleAiPart.text(text)));
        }

        static GoogleAiContent system(String text) {
            return new GoogleAiContent("system", List.of(GoogleAiPart.text(text)));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GoogleAiPart(String text, @JsonProperty("inline_data") GoogleInlineData inlineData) {

        static GoogleAiPart text(String value) {
            return new GoogleAiPart(value != null ? value : "", null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GoogleInlineData(@JsonProperty("mime_type") String mimeType, String data) { }

    record GoogleAiResponse(List<GoogleAiCandidate> candidates, @JsonProperty("prompt_feedback") Map<String, Object> promptFeedback) {

        String firstCandidateText() {
            if (CollectionUtils.isEmpty(candidates)) {
                return null;
            }
            GoogleAiCandidate candidate = candidates.get(0);
            return candidate.text();
        }
    }

    record GoogleAiCandidate(GoogleAiContent content, Map<String, Object> safetyRatings) {

        String text() {
            if (content == null || CollectionUtils.isEmpty(content.parts())) {
                return null;
            }
            return content.parts().stream()
                .map(GoogleAiPart::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        }
    }
}
