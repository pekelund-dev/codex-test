package dev.pekelund.pklnd.receiptparser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP controller that receives Cloud Storage style events forwarded by the web application.
 */
@RestController
@RequestMapping(path = "/events/storage")
public class ReceiptProcessingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessingController.class);

    private final ObjectMapper objectMapper;
    private final ReceiptParsingHandler handler;

    public ReceiptProcessingController(ObjectMapper objectMapper, ReceiptParsingHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
        LOGGER.info("Constructing ReceiptProcessingController with ObjectMapper {} and handler instance id {}",
            objectMapper.getClass().getName(), System.identityHashCode(handler));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> handleStorageEvent(@RequestBody(required = false) String payload,
        @RequestHeader(value = "ce-type", required = false) String cloudEventType,
        @RequestHeader(value = "ce-subject", required = false) String cloudEventSubject,
        @RequestHeader(value = "ce-id", required = false) String cloudEventId) {

        LOGGER.info("ReceiptProcessingController invoked with CloudEvent type={} subject={} id={} payloadLength={}",
            cloudEventType, cloudEventSubject, cloudEventId, payload != null ? payload.length() : null);

        if (!StringUtils.hasText(payload)) {
            LOGGER.warn("Received empty Cloud Storage event payload");
            return ResponseEntity.badRequest().build();
        }

        try (ReceiptProcessingMdc.Context ignored = ReceiptProcessingMdc.open(cloudEventId)) {
            StorageObjectEvent storageObjectEvent = parseStorageObject(payload);
            ReceiptProcessingMdc.attachEvent(storageObjectEvent);
            LOGGER.info("Delegating storage event for bucket {} object {} to handler instance {}", storageObjectEvent.getBucket(),
                storageObjectEvent.getName(), System.identityHashCode(handler));
            handler.handle(storageObjectEvent);
            return ResponseEntity.accepted().build();
        }
    }

    private StorageObjectEvent parseStorageObject(String payload) {
        try {
            return objectMapper.readValue(payload, StorageObjectEvent.class);
        } catch (IOException ex) {
            throw new ReceiptParsingException("Failed to deserialize Cloud Storage event payload", ex);
        }
    }

    @ExceptionHandler(ReceiptParsingException.class)
    ResponseEntity<String> handleParsingException(ReceiptParsingException ex) {
        LOGGER.error("Receipt parsing error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}
