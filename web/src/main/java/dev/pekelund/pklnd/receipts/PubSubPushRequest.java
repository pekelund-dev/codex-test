package dev.pekelund.pklnd.receipts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PubSubPushRequest(
    @JsonProperty("message") PubSubMessage message,
    @JsonProperty("subscription") String subscription
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubSubMessage(
        @JsonProperty("data") String data,
        @JsonProperty("messageId") String messageId,
        @JsonProperty("publishTime") String publishTime,
        @JsonProperty("attributes") Map<String, String> attributes
    ) { }
}
