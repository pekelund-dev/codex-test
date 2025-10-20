package dev.pekelund.pklnd.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Payload published by the Cloud Function when a new receipt upload needs to be
 * processed by the web application.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReceiptProcessingMessage(
    @JsonProperty("bucket") String bucket,
    @JsonProperty("objectName") String objectName,
    @JsonProperty("contentType") String contentType,
    @JsonProperty("size") Long size,
    @JsonProperty("generation") String generation,
    @JsonProperty("metageneration") String metageneration,
    @JsonProperty("timeCreated") Instant timeCreated,
    @JsonProperty("metadata") Map<String, String> metadata
) {

    public ReceiptProcessingMessage {
        if (metadata != null && metadata.isEmpty()) {
            metadata = null;
        }
    }

    /**
     * Convenience factory for building a processing message from a Cloud Storage event.
     */
    public static ReceiptProcessingMessage fromStorageEvent(
        String bucket,
        String objectName,
        String contentType,
        Long size,
        String generation,
        String metageneration,
        Instant timeCreated,
        Map<String, String> metadata) {

        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(objectName, "objectName must not be null");
        return new ReceiptProcessingMessage(bucket, objectName, contentType, size, generation,
            metageneration, timeCreated, metadata);
    }
}
