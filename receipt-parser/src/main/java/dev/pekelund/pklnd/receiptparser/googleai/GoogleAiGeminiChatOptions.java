package dev.pekelund.pklnd.receiptparser.googleai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Chat options used by the Google AI Studio Gemini client.
 */
public class GoogleAiGeminiChatOptions implements ChatOptions {

    private final String model;
    private final Double frequencyPenalty;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final List<String> stopSequences;
    private final Double temperature;
    private final Integer topK;
    private final Double topP;
    private final Integer maxOutputTokens;

    private GoogleAiGeminiChatOptions(Builder builder) {
        this.model = builder.model;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.maxTokens = builder.maxTokens;
        this.presencePenalty = builder.presencePenalty;
        this.stopSequences = builder.stopSequences != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.stopSequences))
            : Collections.emptyList();
        this.temperature = builder.temperature;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.maxOutputTokens = builder.maxOutputTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder()
            .model(this.model)
            .frequencyPenalty(this.frequencyPenalty)
            .maxTokens(this.maxTokens)
            .presencePenalty(this.presencePenalty)
            .stopSequences(this.stopSequences)
            .temperature(this.temperature)
            .topK(this.topK)
            .topP(this.topP)
            .maxOutputTokens(this.maxOutputTokens);
    }

    public GoogleAiGeminiChatOptions merge(GoogleAiGeminiChatOptions overrides) {
        if (overrides == null) {
            return this;
        }
        Builder builder = this.toBuilder();
        if (StringUtils.hasText(overrides.getModel())) {
            builder.model(overrides.getModel());
        }
        if (overrides.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(overrides.getFrequencyPenalty());
        }
        if (overrides.getMaxTokens() != null) {
            builder.maxTokens(overrides.getMaxTokens());
        }
        if (overrides.getPresencePenalty() != null) {
            builder.presencePenalty(overrides.getPresencePenalty());
        }
        if (!CollectionUtils.isEmpty(overrides.getStopSequences())) {
            builder.stopSequences(overrides.getStopSequences());
        }
        if (overrides.getTemperature() != null) {
            builder.temperature(overrides.getTemperature());
        }
        if (overrides.getTopK() != null) {
            builder.topK(overrides.getTopK());
        }
        if (overrides.getTopP() != null) {
            builder.topP(overrides.getTopP());
        }
        if (overrides.getMaxOutputTokens() != null) {
            builder.maxOutputTokens(overrides.getMaxOutputTokens());
        }
        return builder.build();
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    @Override
    public Integer getMaxTokens() {
        return maxTokens;
    }

    @Override
    public Double getPresencePenalty() {
        return presencePenalty;
    }

    @Override
    public List<String> getStopSequences() {
        return stopSequences;
    }

    @Override
    public Double getTemperature() {
        return temperature;
    }

    @Override
    public Integer getTopK() {
        return topK;
    }

    @Override
    public Double getTopP() {
        return topP;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ChatOptions> T copy() {
        return (T) this.toBuilder().build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GoogleAiGeminiChatOptions that)) {
            return false;
        }
        return Objects.equals(model, that.model)
            && Objects.equals(frequencyPenalty, that.frequencyPenalty)
            && Objects.equals(maxTokens, that.maxTokens)
            && Objects.equals(presencePenalty, that.presencePenalty)
            && Objects.equals(stopSequences, that.stopSequences)
            && Objects.equals(temperature, that.temperature)
            && Objects.equals(topK, that.topK)
            && Objects.equals(topP, that.topP)
            && Objects.equals(maxOutputTokens, that.maxOutputTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, frequencyPenalty, maxTokens, presencePenalty, stopSequences, temperature, topK, topP,
            maxOutputTokens);
    }

    @Override
    public String toString() {
        return "GoogleAiGeminiChatOptions{"
            + "model='" + model + '\''
            + ", temperature=" + temperature
            + ", topP=" + topP
            + ", topK=" + topK
            + ", maxTokens=" + maxTokens
            + ", maxOutputTokens=" + maxOutputTokens
            + '}';
    }

    /**
     * Builder for {@link GoogleAiGeminiChatOptions}.
     */
    public static class Builder implements ChatOptions.Builder {

        private String model;
        private Double frequencyPenalty;
        private Integer maxTokens;
        private Double presencePenalty;
        private List<String> stopSequences;
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Integer maxOutputTokens;

        @Override
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        @Override
        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        @Override
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        @Override
        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        @Override
        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        @Override
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        @Override
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        @Override
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        @Override
        public GoogleAiGeminiChatOptions build() {
            return new GoogleAiGeminiChatOptions(this);
        }
    }
}

