package dev.pekelund.pklnd.function;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageObjectEvent {

    private String bucket;
    private String name;
    private String contentType;
    private Long size;
    private String generation;
    private String metageneration;
    private String timeCreated;
    private Map<String, String> metadata;

    @JsonProperty("bucket")
    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("contentType")
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @JsonProperty("size")
    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    @JsonProperty("generation")
    public String getGeneration() {
        return generation;
    }

    public void setGeneration(String generation) {
        this.generation = generation;
    }

    @JsonProperty("metageneration")
    public String getMetageneration() {
        return metageneration;
    }

    public void setMetageneration(String metageneration) {
        this.metageneration = metageneration;
    }

    @JsonProperty("timeCreated")
    public String getTimeCreated() {
        return timeCreated;
    }

    public void setTimeCreated(String timeCreated) {
        this.timeCreated = timeCreated;
    }

    @JsonProperty("metadata")
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Instant getTimeCreatedInstant() {
        if (timeCreated == null || timeCreated.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timeCreated).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
