package dev.pekelund.pklnd.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloud Function entry point for receipt parsing.
 */
public class ReceiptProcessingFunction implements Consumer<String> {

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
    public void accept(String payload) {
        LOGGER.info("ReceiptProcessingFunction.accept invoked with payload length {}", payload != null ? payload.length() : null);
        if (payload == null || payload.isBlank()) {
            LOGGER.warn("Received null or empty payload");
            return;
        }
        StorageObjectEvent storageObjectEvent = parseStorageObject(payload);
        LOGGER.info("ReceiptProcessingFunction delegating to handler {} for bucket {} object {}",
            System.identityHashCode(handler), storageObjectEvent != null ? storageObjectEvent.getBucket() : null,
            storageObjectEvent != null ? storageObjectEvent.getName() : null);
        handler.handle(storageObjectEvent);
    }

    private StorageObjectEvent parseStorageObject(String payload) {
        try {
            return objectMapper.readValue(payload, StorageObjectEvent.class);
        } catch (IOException ex) {
            throw new ReceiptParsingException("Failed to deserialize Cloud Storage event payload", ex);
        }
    }
}
