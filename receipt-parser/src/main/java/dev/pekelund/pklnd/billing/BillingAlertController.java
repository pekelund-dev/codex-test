package dev.pekelund.pklnd.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Controller that receives billing budget alerts from GCP Pub/Sub.
 * When a budget threshold of 100% is exceeded, triggers application shutdown
 * to stop generating costs.
 */
@RestController
public class BillingAlertController {
    private static final Logger logger = LoggerFactory.getLogger(BillingAlertController.class);
    
    private final BillingShutdownService shutdownService;
    private final ObjectMapper objectMapper;
    
    public BillingAlertController(BillingShutdownService shutdownService, ObjectMapper objectMapper) {
        this.shutdownService = shutdownService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Receives billing budget alerts from GCP Pub/Sub.
     * Expected message format:
     * {
     *   "message": {
     *     "data": "base64-encoded-json",
     *     "messageId": "...",
     *     "publishTime": "..."
     *   },
     *   "subscription": "..."
     * }
     * 
     * The decoded data contains budget alert information including thresholdPercent.
     */
    @PostMapping("/api/billing/alerts")
    public ResponseEntity<String> handleBillingAlert(@RequestBody String rawBody) {
        try {
            logger.info("Received billing alert notification");
            
            // Parse the Pub/Sub message
            JsonNode pubsubMessage = objectMapper.readTree(rawBody);
            JsonNode message = pubsubMessage.get("message");
            
            if (message == null || !message.has("data")) {
                logger.warn("Invalid Pub/Sub message format: no message.data field");
                return ResponseEntity.badRequest().body("Invalid message format");
            }
            
            // Decode the base64-encoded data
            String encodedData = message.get("data").asText();
            byte[] decodedBytes = Base64.getDecoder().decode(encodedData);
            String decodedData = new String(decodedBytes);
            
            logger.info("Decoded billing alert data: {}", decodedData);
            
            // Parse the budget alert data
            JsonNode alertData = objectMapper.readTree(decodedData);
            
            // Check if this is a 100% threshold breach
            double thresholdPercent = 0.0;
            if (alertData.has("costAmount") && alertData.has("budgetAmount")) {
                double costAmount = alertData.get("costAmount").asDouble();
                double budgetAmount = alertData.get("budgetAmount").asDouble();
                if (budgetAmount > 0) {
                    thresholdPercent = (costAmount / budgetAmount) * 100.0;
                }
            } else if (alertData.has("alertThresholdExceeded")) {
                // Some budget alert formats include threshold information directly
                thresholdPercent = alertData.get("alertThresholdExceeded").asDouble() * 100.0;
            }
            
            logger.info("Budget threshold: {}%", thresholdPercent);
            
            // Trigger shutdown if we've exceeded or are at 100% of budget
            if (thresholdPercent >= 100.0) {
                String reason = String.format("Budget exceeded: %.1f%%", thresholdPercent);
                shutdownService.triggerShutdown(reason);
                logger.error("SHUTDOWN TRIGGERED: Budget at {}%", thresholdPercent);
                return ResponseEntity.ok("Shutdown triggered due to budget exceeded");
            } else {
                logger.warn("Budget alert received at {}% - monitoring", thresholdPercent);
                return ResponseEntity.ok("Budget alert received - monitoring");
            }
            
        } catch (Exception e) {
            logger.error("Error processing billing alert", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing billing alert");
        }
    }
}
