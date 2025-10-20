package dev.pekelund.pklnd.receipts;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pekelund.pklnd.messaging.ReceiptProcessingMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/pubsub")
public class ReceiptProcessingPushController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptProcessingPushController.class);

    private final ObjectMapper objectMapper;
    private final ReceiptProcessingService receiptProcessingService;
    private final ReceiptProcessingProperties properties;

    public ReceiptProcessingPushController(ObjectMapper objectMapper,
        ReceiptProcessingService receiptProcessingService,
        ReceiptProcessingProperties properties) {
        this.objectMapper = objectMapper;
        this.receiptProcessingService = receiptProcessingService;
        this.properties = properties;
    }

    @PostMapping("/receipt-processing")
    public ResponseEntity<String> handleMessage(
        @RequestBody(required = false) PubSubPushRequest request,
        @RequestHeader(name = "Ce-Token", required = false) String verificationToken) {

        if (!isTokenValid(verificationToken)) {
            LOGGER.warn("Rejecting Pub/Sub push with invalid verification token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid verification token");
        }

        if (request == null || request.message() == null) {
            LOGGER.warn("Received empty Pub/Sub push request");
            return ResponseEntity.badRequest().body("missing message");
        }

        String data = request.message().data();
        if (!StringUtils.hasText(data)) {
            LOGGER.warn("Pub/Sub push request did not include data field");
            return ResponseEntity.badRequest().body("missing data");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(data);
            String json = new String(decoded, StandardCharsets.UTF_8);
            LOGGER.info("Decoded Pub/Sub message {} bytes", decoded.length);
            ReceiptProcessingMessage message = objectMapper.readValue(json, ReceiptProcessingMessage.class);
            receiptProcessingService.process(message);
            return ResponseEntity.ok("acknowledged");
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Failed to base64 decode Pub/Sub payload", ex);
            return ResponseEntity.badRequest().body("invalid base64 payload");
        } catch (Exception ex) {
            LOGGER.error("Failed to process Pub/Sub push request", ex);
            return ResponseEntity.internalServerError().body("processing failed");
        }
    }

    private boolean isTokenValid(String providedToken) {
        String expected = properties.getPubsubVerificationToken();
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return Objects.equals(expected, providedToken);
    }
}
