package dev.pekelund.responsiveauth.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloud Function entry point for receipt parsing.
 */
public class ReceiptProcessingFunction implements Consumer<CloudEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessingFunction.class);

    private final ObjectMapper objectMapper;
    private final ReceiptParsingHandler handler;

    public ReceiptProcessingFunction(ObjectMapper objectMapper, ReceiptParsingHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
        LOGGER.info("Constructing ReceiptProcessingFunction with ObjectMapper {} and handler instance id {}",
            objectMapper.getClass().getName(), System.identityHashCode(handler));
    }

    @Override
    public void accept(CloudEvent cloudEvent) {
        LOGGER.info("ReceiptProcessingFunction.accept invoked with CloudEvent id {}", cloudEvent != null ? cloudEvent.getId() : null);
        if (cloudEvent == null) {
            LOGGER.warn("Received null CloudEvent");
            return;
        }
        LOGGER.info("ReceiptProcessingFunction triggered - id: {}, type: {}, subject: {}, source: {}",
            cloudEvent.getId(), cloudEvent.getType(), cloudEvent.getSubject(), cloudEvent.getSource());
        if (cloudEvent.getData() == null) {
            LOGGER.warn("CloudEvent did not contain any data");
            return;
        }
        byte[] payload = cloudEvent.getData().toBytes();
        StorageObjectEvent storageObjectEvent = parseStorageObject(payload);
        LOGGER.info("ReceiptProcessingFunction delegating to handler {} for bucket {} object {}",
            System.identityHashCode(handler), storageObjectEvent != null ? storageObjectEvent.getBucket() : null,
            storageObjectEvent != null ? storageObjectEvent.getName() : null);
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
