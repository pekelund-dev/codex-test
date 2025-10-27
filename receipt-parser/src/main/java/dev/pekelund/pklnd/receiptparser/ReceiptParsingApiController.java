package dev.pekelund.pklnd.receiptparser;

import dev.pekelund.pklnd.receiptparser.ReceiptParserRegistry.ParserRegistration;
import dev.pekelund.pklnd.receiptparser.ReceiptParserRegistry.ReceiptParserDescriptor;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public REST API that exposes ad-hoc receipt parsing endpoints. These APIs allow
 * clients to upload a PDF file, run it through one of the available parsers, and
 * receive the structured extraction result without persisting anything to Firestore.
 */
@RestController
@RequestMapping(path = "/api/parsers")
@Profile("!local-receipt-test")
public class ReceiptParsingApiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptParsingApiController.class);

    private static final String PDF_MIME_TYPE = "application/pdf";

    private final ReceiptParserRegistry parserRegistry;

    public ReceiptParsingApiController(ReceiptParserRegistry parserRegistry) {
        this.parserRegistry = parserRegistry;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<ReceiptParserDescriptor> listSupportedParsers() {
        return parserRegistry.listParsers();
    }

    @PostMapping(path = "/{parserId}/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReceiptParsingResponse> parseReceipt(@PathVariable("parserId") String parserId,
        @RequestPart("file") MultipartFile file) throws IOException {

        ParserRegistration registration = parserRegistry.find(parserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Unknown parser: " + parserId));

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A non-empty PDF must be provided as the 'file' part");
        }

        if (!isPdf(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Only PDF uploads are supported");
        }

        String fileName = determineFileName(file);
        LOGGER.info("Parsing receipt '{}' using parser '{}'", fileName, registration.descriptor().id());
        ReceiptExtractionResult extractionResult = registration.extractor().extract(file.getBytes(), fileName);
        LOGGER.info("Parser '{}' completed with {} top-level fields", registration.descriptor().id(),
            extractionResult.structuredData() != null ? extractionResult.structuredData().size() : 0);

        ReceiptParsingResponse response = new ReceiptParsingResponse(registration.descriptor(),
            extractionResult.structuredData(), extractionResult.rawResponse());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(ReceiptParsingException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Object> handleParsingException(ReceiptParsingException exception) {
        LOGGER.warn("Receipt parsing failed: {}", exception.getMessage());
        return Map.of("error", exception.getMessage());
    }

    private boolean isPdf(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            String normalised = contentType.toLowerCase();
            if (PDF_MIME_TYPE.equals(normalised) || normalised.startsWith(PDF_MIME_TYPE)) {
                return true;
            }
        }
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".pdf");
    }

    private String determineFileName(MultipartFile file) {
        if (file == null) {
            return "receipt.pdf";
        }
        String fileName = file.getOriginalFilename();
        if (StringUtils.hasText(fileName)) {
            return fileName;
        }
        return "receipt.pdf";
    }

    public record ReceiptParsingResponse(ReceiptParserDescriptor parser, Map<String, Object> structuredData,
        String rawResponse) { }
}
