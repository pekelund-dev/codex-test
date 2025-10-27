package dev.pekelund.pklnd.receiptparser.googleai;

import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Chat options used by the Google AI Studio Gemini client.
 */
public class GoogleAiGeminiChatOptions {

    private final String model;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final Integer maxOutputTokens;

    private GoogleAiGeminiChatOptions(Builder builder) {
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.maxOutputTokens = builder.maxOutputTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder()
            .model(this.model)
            .temperature(this.temperature)
            .topP(this.topP)
            .topK(this.topK)
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
        if (overrides.getTemperature() != null) {
            builder.temperature(overrides.getTemperature());
        }
        if (overrides.getTopP() != null) {
            builder.topP(overrides.getTopP());
        }
        if (overrides.getTopK() != null) {
            builder.topK(overrides.getTopK());
        }
        if (overrides.getMaxOutputTokens() != null) {
            builder.maxOutputTokens(overrides.getMaxOutputTokens());
        }
        return builder.build();
    }

    public String getModel() {
        return model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
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
            && Objects.equals(temperature, that.temperature)
            && Objects.equals(topP, that.topP)
            && Objects.equals(topK, that.topK)
            && Objects.equals(maxOutputTokens, that.maxOutputTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, temperature, topP, topK, maxOutputTokens);
    }

    @Override
    public String toString() {
        return "GoogleAiGeminiChatOptions{"
            + "model='" + model + '\''
            + ", temperature=" + temperature
            + ", topP=" + topP
            + ", topK=" + topK
            + ", maxOutputTokens=" + maxOutputTokens
            + '}';
    }

    /**
     * Builder for {@link GoogleAiGeminiChatOptions}.
     */
    public static class Builder {

        private String model;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Integer maxOutputTokens;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public GoogleAiGeminiChatOptions build() {
            return new GoogleAiGeminiChatOptions(this);
        }
    }
}
