package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cloud Function entry point for receipt parsing.
 */
@Component("receiptProcessingFunction")
public class ReceiptProcessingFunction implements Consumer<CloudEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessingFunction.class);

    private final ObjectMapper objectMapper;
    private final ReceiptParsingHandler handler;

    public ReceiptProcessingFunction(ObjectMapper objectMapper, ReceiptParsingHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @Override
    public void accept(CloudEvent cloudEvent) {
        if (cloudEvent == null) {
            LOGGER.warn("Received null CloudEvent");
            return;
        }
        if (cloudEvent.getData() == null) {
            LOGGER.warn("CloudEvent did not contain any data");
            return;
        }
        byte[] payload = cloudEvent.getData().toBytes();
        StorageObjectEvent storageObjectEvent = parseStorageObject(payload);
        handler.handle(storageObjectEvent);
    }

    private StorageObjectEvent parseStorageObject(byte[] payload) {
        try {
            return objectMapper.readValue(payload, StorageObjectEvent.class);
        } catch (IOException ex) {
            throw new ReceiptParsingException("Failed to deserialize Cloud Storage event payload", ex);
        }
    }
}
