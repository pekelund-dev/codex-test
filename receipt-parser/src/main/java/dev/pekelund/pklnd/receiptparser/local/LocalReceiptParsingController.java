package dev.pekelund.pklnd.receiptparser.local;

import dev.pekelund.pklnd.receiptparser.ReceiptDataExtractor;
import dev.pekelund.pklnd.receiptparser.ReceiptExtractionResult;
import dev.pekelund.pklnd.receiptparser.ReceiptParsingException;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.context.annotation.Profile;

@RestController
@RequestMapping("/local-receipts")
@Profile("local-receipt-test")
public class LocalReceiptParsingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalReceiptParsingController.class);

    private final ReceiptDataExtractor receiptDataExtractor;
    private final ReceiptDataExtractor codexReceiptDataExtractor;

    public LocalReceiptParsingController(ReceiptDataExtractor receiptDataExtractor,
        @Qualifier("codexReceiptDataExtractor") ReceiptDataExtractor codexReceiptDataExtractor) {
        this.receiptDataExtractor = receiptDataExtractor;
        this.codexReceiptDataExtractor = codexReceiptDataExtractor;
    }

    @PostMapping(path = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> parseReceipt(@RequestPart("file") MultipartFile file) throws IOException {
        return parseReceiptInternal(file, receiptDataExtractor, "legacy");
    }

    @PostMapping(path = "/parse-codex", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> parseReceiptCodex(@RequestPart("file") MultipartFile file) throws IOException {
        return parseReceiptInternal(file, codexReceiptDataExtractor, "codex");
    }

    @ExceptionHandler(ReceiptParsingException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Object> handleParsingException(ReceiptParsingException exception) {
        LOGGER.warn("Receipt parsing failed: {}", exception.getMessage());
        return Map.of(
            "error", exception.getMessage()
        );
    }

    private ResponseEntity<Map<String, Object>> parseReceiptInternal(MultipartFile file, ReceiptDataExtractor extractor,
        String extractorName) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "A non-empty PDF must be provided as the 'file' part"
            ));
        }

        String fileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "receipt.pdf";
        LOGGER.info("Parsing receipt '{}' using {} extractor", fileName, extractorName);
        ReceiptExtractionResult result = extractor.extract(file.getBytes(), fileName);
        LOGGER.info("Parsed receipt '{}' with {} structured fields via {} extractor", fileName,
            result.structuredData().size(), extractorName);
        return ResponseEntity.ok(Map.of(
            "structuredData", result.structuredData(),
            "rawResponse", result.rawResponse()
        ));
    }
}
