package dev.pekelund.pklnd.receiptparser.local;

import dev.pekelund.pklnd.receiptparser.ReceiptDataExtractor;
import dev.pekelund.pklnd.receiptparser.ReceiptExtractionResult;
import dev.pekelund.pklnd.receiptparser.ReceiptExtractionRepository;
import dev.pekelund.pklnd.receiptparser.ReceiptParsingException;
import dev.pekelund.pklnd.receiptparser.ReceiptProcessingStatus;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/local-receipts")
@Profile("local")
public class LocalReceiptIngestionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalReceiptIngestionController.class);

    private static final String DEFAULT_BUCKET = "local-receipts";

    private final ReceiptExtractionRepository receiptExtractionRepository;
    private final ReceiptDataExtractor receiptDataExtractor;

    public LocalReceiptIngestionController(ReceiptExtractionRepository receiptExtractionRepository,
        ReceiptDataExtractor receiptDataExtractor) {
        this.receiptExtractionRepository = receiptExtractionRepository;
        this.receiptDataExtractor = receiptDataExtractor;
    }

    @PostMapping(path = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ingestAndStore(@RequestPart("file") MultipartFile file,
        @RequestParam("userId") String userId,
        @RequestParam(value = "userEmail", required = false) String userEmail,
        @RequestParam(value = "userName", required = false) String userName,
        @RequestParam(value = "bucket", required = false) String bucket,
        @RequestParam(value = "objectName", required = false) String objectName) throws IOException {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "A non-empty PDF must be provided as the 'file' part"
            ));
        }

        if (!StringUtils.hasText(userId)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "A userId parameter must be provided"
            ));
        }

        String resolvedBucket = StringUtils.hasText(bucket) ? bucket : DEFAULT_BUCKET;
        String resolvedObjectName = StringUtils.hasText(objectName)
            ? objectName
            : generateObjectName(file.getOriginalFilename());
        ReceiptOwner owner = new ReceiptOwner(userId, userName, userEmail);

        LOGGER.info("Ingesting local receipt for bucket={} object={} owner={}",
            resolvedBucket, resolvedObjectName, owner);

        receiptExtractionRepository.markStatus(resolvedBucket, resolvedObjectName, owner,
            ReceiptProcessingStatus.RECEIVED, "Local upload received");

        try {
            receiptExtractionRepository.markStatus(resolvedBucket, resolvedObjectName, owner,
                ReceiptProcessingStatus.PARSING, "Local parsing started");

            ReceiptExtractionResult result = receiptDataExtractor.extract(file.getBytes(), resolvedObjectName);

            receiptExtractionRepository.saveExtraction(resolvedBucket, resolvedObjectName, owner, result,
                "Local parsing completed");

            Map<String, Object> response = new HashMap<>();
            response.put("bucket", resolvedBucket);
            response.put("objectName", resolvedObjectName);
            response.put("owner", owner.toAttributes());
            response.put("structuredData", result.structuredData());
            response.put("rawResponse", result.rawResponse());
            return ResponseEntity.ok(response);
        } catch (ReceiptParsingException ex) {
            LOGGER.error("Local receipt parsing failed for {}/{}", resolvedBucket, resolvedObjectName, ex);
            receiptExtractionRepository.markFailure(resolvedBucket, resolvedObjectName, owner,
                "Local parsing failed", ex);
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("Unexpected error during local receipt ingestion for {}/{}", resolvedBucket, resolvedObjectName, ex);
            receiptExtractionRepository.markFailure(resolvedBucket, resolvedObjectName, owner,
                "Unexpected internal error: " + ex.getMessage(), ex);
            throw new RuntimeException("Unexpected error processing receipt", ex);
        }
    }

    @ExceptionHandler(ReceiptParsingException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Object> handleParsingException(ReceiptParsingException exception) {
        LOGGER.warn("Local receipt parsing failed: {}", exception.getMessage());
        return Map.of(
            "error", exception.getMessage()
        );
    }

    private String generateObjectName(String originalFilename) {
        String sanitized = StringUtils.hasText(originalFilename)
            ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_")
            : "receipt.pdf";
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        return timestamp + "-" + UUID.randomUUID() + "-" + sanitized;
    }
}
