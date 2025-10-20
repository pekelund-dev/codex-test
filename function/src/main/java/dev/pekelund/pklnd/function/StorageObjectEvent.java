package dev.pekelund.pklnd.function;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Minimal representation of the Cloud Storage event payload used by the receipt processor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageObjectEvent {

    private String bucket;
    private String name;

    @JsonProperty("contentType")
    private String contentType;

    private Map<String, String> metadata;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
