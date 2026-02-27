package dev.pekelund.pklnd.web.receipts;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionAccessException;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient.ProcessingFailure;
import dev.pekelund.pklnd.receipts.ReceiptProcessingClient.ProcessingResult;
import dev.pekelund.pklnd.storage.ReceiptFile;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.storage.ReceiptOwnerMatcher;
import dev.pekelund.pklnd.storage.ReceiptStorageException;
import dev.pekelund.pklnd.storage.ReceiptStorageService;
import dev.pekelund.pklnd.storage.StoredReceiptReference;
import dev.pekelund.pklnd.web.ReceiptOwnerResolver;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReceiptUploadController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptUploadController.class);
    private static final int MAX_UPLOAD_FILES = 50;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Optional<ReceiptStorageService> receiptStorageService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;
    private final Optional<ReceiptProcessingClient> receiptProcessingClient;
    private final ReceiptScopeHelper scopeHelper;

    public ReceiptUploadController(
        @Autowired(required = false) ReceiptStorageService receiptStorageService,
        @Autowired(required = false) ReceiptExtractionService receiptExtractionService,
        ReceiptOwnerResolver receiptOwnerResolver,
        @Autowired(required = false) ReceiptProcessingClient receiptProcessingClient,
        ReceiptScopeHelper scopeHelper
    ) {
        this.receiptStorageService = Optional.ofNullable(receiptStorageService);
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
        this.receiptOwnerResolver = receiptOwnerResolver;
        this.receiptProcessingClient = Optional.ofNullable(receiptProcessingClient);
        this.scopeHelper = scopeHelper;
    }

    @GetMapping("/receipts/uploads")
    public String receiptUploads(
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        ReceiptViewScope scope = scopeHelper.resolveScope(scopeParam, authentication);
        ReceiptPageData pageData = loadReceiptPageData(authentication, scope);
        boolean canViewAll = scopeHelper.isAdmin(authentication);

        model.addAttribute("pageTitle", "Upload receipts");
        model.addAttribute("storageEnabled", pageData.storageEnabled());
        model.addAttribute("maxUploadFiles", MAX_UPLOAD_FILES);
        model.addAttribute("files", pageData.files());
        model.addAttribute("listingError", pageData.listingError());
        model.addAttribute("fileStatuses", pageData.fileStatuses());
        model.addAttribute("scopeParam", scopeHelper.toScopeParameter(scope));
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("viewingAll", pageData.viewingAll());
        return "receipt-uploads";
    }

    @PostMapping("/receipts/upload")
    public String uploadReceipts(
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        RedirectAttributes redirectAttributes,
        Authentication authentication
    ) {
        UploadOutcome outcome = processUpload(files, authentication);
        if (outcome.successMessage() != null) {
            redirectAttributes.addFlashAttribute("successMessage", outcome.successMessage());
        }
        if (outcome.errorMessage() != null) {
            redirectAttributes.addFlashAttribute("errorMessage", outcome.errorMessage());
        }
        return "redirect:/receipts/uploads";
    }

    @PostMapping(value = "/receipts/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ReceiptUploadResponse> uploadReceiptsJson(
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        Authentication authentication
    ) {
        UploadOutcome outcome = processUpload(files, authentication);
        HttpStatus status = outcome.errorMessage() != null && outcome.successMessage() == null
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.OK;

        return ResponseEntity.status(status)
            .body(new ReceiptUploadResponse(outcome.successMessage(), outcome.errorMessage()));
    }

    private UploadOutcome processUpload(List<MultipartFile> files, Authentication authentication) {
        ReceiptStorageService storage = receiptStorageService
            .filter(ReceiptStorageService::isEnabled)
            .orElse(null);

        if (storage == null) {
            return new UploadOutcome(null,
                "Uppladdning av kvitton är inaktiverad. Konfigurera Google Cloud Storage för att aktivera funktionen.");
        }

        List<MultipartFile> sanitizedFiles = files == null ? List.of()
            : files.stream().filter(file -> file != null && !file.isEmpty()).toList();

        if (sanitizedFiles.isEmpty()) {
            return new UploadOutcome(null, "Välj minst en fil att ladda upp.");
        }

        if (sanitizedFiles.size() > MAX_UPLOAD_FILES) {
            return new UploadOutcome(null,
                "Du kan ladda upp högst %d filer åt gången.".formatted(MAX_UPLOAD_FILES));
        }

        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);

        try {
            dev.pekelund.pklnd.storage.UploadResult uploadResult = storage.uploadFilesWithResults(sanitizedFiles, owner);
            List<StoredReceiptReference> uploadedReferences = uploadResult.uploadedReceipts();
            int uploadedCount = uploadedReferences.size();

            String successMessage = null;
            String errorMessage = null;

            if (uploadedCount > 0) {
                successMessage = uploadedCount == 1
                    ? "1 fil laddades upp."
                    : "%d filer laddades upp.".formatted(uploadedCount);
            }

            if (uploadedCount > 0 && receiptProcessingClient.isPresent()) {
                ProcessingResult processingResult = receiptProcessingClient.get().notifyUploads(uploadedReferences);
                if (processingResult.succeededCount() > 0) {
                    int queued = processingResult.succeededCount();
                    successMessage = queued == 1
                        ? "1 fil laddades upp och köades för tolkning."
                        : "%d filer laddades upp och köades för tolkning.".formatted(queued);
                }
                if (!processingResult.failures().isEmpty()) {
                    String parsingErrors = formatProcessingFailure(processingResult.failures());
                    errorMessage = errorMessage != null ? errorMessage + " " + parsingErrors : parsingErrors;
                    LOGGER.warn("Failed to queue {} receipt(s) for parsing", processingResult.failures().size());
                }
            }

            if (uploadResult.hasFailures()) {
                String uploadErrors = formatUploadFailures(uploadResult.failures());
                errorMessage = errorMessage != null ? errorMessage + " " + uploadErrors : uploadErrors;
            }

            if (uploadedCount == 0 && uploadResult.hasFailures()) {
                return new UploadOutcome(null, errorMessage);
            }

            return new UploadOutcome(successMessage, errorMessage);
        } catch (ReceiptStorageException ex) {
            LOGGER.error("Failed to upload receipts", ex);
            return new UploadOutcome(null, ex.getMessage());
        }
    }

    private String formatUploadFailures(List<dev.pekelund.pklnd.storage.UploadFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return null;
        }

        long duplicateCount = failures.stream().filter(dev.pekelund.pklnd.storage.UploadFailure::isDuplicate).count();
        long errorCount = failures.size() - duplicateCount;

        StringBuilder message = new StringBuilder();

        if (duplicateCount > 0) {
            if (duplicateCount == 1) {
                String filename = failures.stream()
                    .filter(dev.pekelund.pklnd.storage.UploadFailure::isDuplicate)
                    .findFirst()
                    .map(dev.pekelund.pklnd.storage.UploadFailure::filename)
                    .orElse("okänd fil");
                message.append("Kvittot '").append(filename).append("' har redan laddats upp tidigare.");
            } else {
                message.append(duplicateCount).append(" kvitton har redan laddats upp tidigare.");
            }
        }

        if (errorCount > 0) {
            if (message.length() > 0) {
                message.append(" ");
            }
            message.append(errorCount == 1 ? "1 fil" : errorCount + " filer")
                .append(" kunde inte laddas upp på grund av ett fel.");
        }

        return message.toString();
    }

    private String formatProcessingFailure(List<ProcessingFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return null;
        }

        String joined = failures.stream()
            .map(failure -> failure.reference().objectName())
            .limit(3)
            .collect(Collectors.joining(", "));
        if (failures.size() > 3) {
            joined = joined + " …";
        }
        return "Vissa uppladdningar kunde inte köas för tolkning: %s.".formatted(joined);
    }

    private ReceiptPageData loadReceiptPageData(Authentication authentication, ReceiptViewScope scope) {
        boolean storageEnabled = receiptStorageService.isPresent() && receiptStorageService.get().isEnabled();
        List<ReceiptFile> receiptFiles = List.of();
        String listingError = null;
        boolean viewingAll = scopeHelper.isViewingAll(scope, authentication);

        if (storageEnabled) {
            try {
                receiptFiles = receiptStorageService.get().listReceipts();
            } catch (ReceiptStorageException ex) {
                listingError = ex.getMessage();
                LOGGER.warn("Failed to list receipt files", ex);
            }
        }

        boolean parsedReceiptsEnabled = receiptExtractionService.isPresent() && receiptExtractionService.get().isEnabled();
        List<ParsedReceipt> parsedReceipts = List.of();
        String parsedListingError = null;

        ReceiptOwner currentOwner = receiptOwnerResolver.resolve(authentication);
        if (currentOwner == null && !viewingAll) {
            receiptFiles = List.of();
        } else {
            if (!viewingAll) {
                receiptFiles = receiptFiles.stream()
                    .filter(file -> ReceiptOwnerMatcher.belongsToCurrentOwner(file.owner(), currentOwner))
                    .toList();
            }

            if (parsedReceiptsEnabled) {
                try {
                    parsedReceipts = viewingAll
                        ? receiptExtractionService.get().listAllReceipts()
                        : receiptExtractionService.get().listReceiptsForOwner(currentOwner);
                } catch (ReceiptExtractionAccessException ex) {
                    parsedListingError = ex.getMessage();
                    LOGGER.warn("Failed to list parsed receipts", ex);
                }
            }
        }

        Map<String, ParsedReceipt> fileStatuses = parsedReceipts.stream()
            .filter(parsed -> parsed != null && StringUtils.hasText(parsed.objectName()))
            .collect(Collectors.toMap(
                ParsedReceipt::objectName,
                Function.identity(),
                (existing, replacement) -> replacement,
                LinkedHashMap::new
            ));

        return new ReceiptPageData(
            storageEnabled,
            receiptFiles,
            listingError,
            parsedReceiptsEnabled,
            parsedReceipts,
            parsedListingError,
            fileStatuses,
            viewingAll
        );
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return TIMESTAMP_FORMATTER.format(instant);
    }

    private record ReceiptPageData(
        boolean storageEnabled,
        List<ReceiptFile> files,
        String listingError,
        boolean parsedReceiptsEnabled,
        List<ParsedReceipt> parsedReceipts,
        String parsedListingError,
        Map<String, ParsedReceipt> fileStatuses,
        boolean viewingAll
    ) {
    }

    private record ReceiptUploadResponse(String successMessage, String errorMessage) {
    }

    private record UploadOutcome(String successMessage, String errorMessage) {
    }
}
