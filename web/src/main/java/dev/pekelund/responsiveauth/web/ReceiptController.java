package dev.pekelund.responsiveauth.web;

import dev.pekelund.responsiveauth.firestore.FirestoreUserDetails;
import dev.pekelund.responsiveauth.firestore.ParsedReceipt;
import dev.pekelund.responsiveauth.firestore.ReceiptExtractionAccessException;
import dev.pekelund.responsiveauth.firestore.ReceiptExtractionService;
import dev.pekelund.responsiveauth.storage.ReceiptFile;
import dev.pekelund.responsiveauth.storage.ReceiptOwner;
import dev.pekelund.responsiveauth.storage.ReceiptOwnerMatcher;
import dev.pekelund.responsiveauth.storage.ReceiptStorageException;
import dev.pekelund.responsiveauth.storage.ReceiptStorageService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReceiptController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Optional<ReceiptStorageService> receiptStorageService;
    private final Optional<ReceiptExtractionService> receiptExtractionService;

    public ReceiptController(
        @Autowired(required = false) ReceiptStorageService receiptStorageService,
        @Autowired(required = false) ReceiptExtractionService receiptExtractionService
    ) {
        this.receiptStorageService = Optional.ofNullable(receiptStorageService);
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
    }

    @GetMapping("/receipts")
    public String receipts(Model model, Authentication authentication) {
        ReceiptPageData pageData = loadReceiptPageData(authentication);

        model.addAttribute("pageTitle", "Receipts");
        model.addAttribute("storageEnabled", pageData.storageEnabled());
        model.addAttribute("files", pageData.files());
        model.addAttribute("listingError", pageData.listingError());
        model.addAttribute("parsedReceiptsEnabled", pageData.parsedReceiptsEnabled());
        model.addAttribute("parsedReceipts", pageData.parsedReceipts());
        model.addAttribute("parsedListingError", pageData.parsedListingError());
        model.addAttribute("fileStatuses", pageData.fileStatuses());
        return "receipts";
    }

    @GetMapping(value = "/receipts/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ReceiptDashboardResponse receiptsDashboard(Authentication authentication) {
        ReceiptPageData pageData = loadReceiptPageData(authentication);

        List<ReceiptFileEntry> fileEntries = pageData.files().stream()
            .map(file -> toReceiptFileEntry(file, pageData.fileStatuses()))
            .toList();

        List<ParsedReceiptEntry> parsedEntries = pageData.parsedReceipts().stream()
            .map(this::toParsedReceiptEntry)
            .toList();

        return new ReceiptDashboardResponse(
            pageData.storageEnabled(),
            pageData.listingError(),
            fileEntries,
            pageData.parsedReceiptsEnabled(),
            pageData.parsedListingError(),
            parsedEntries
        );
    }

    private ReceiptPageData loadReceiptPageData(Authentication authentication) {
        boolean storageEnabled = receiptStorageService.isPresent() && receiptStorageService.get().isEnabled();
        List<ReceiptFile> receiptFiles = List.of();
        String listingError = null;

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

        ReceiptOwner currentOwner = resolveReceiptOwner(authentication);
        if (currentOwner == null) {
            receiptFiles = List.of();
        } else {
            receiptFiles = receiptFiles.stream()
                .filter(file -> ReceiptOwnerMatcher.belongsToCurrentOwner(file.owner(), currentOwner))
                .toList();

            if (parsedReceiptsEnabled) {
                try {
                    parsedReceipts = receiptExtractionService.get().listReceiptsForOwner(currentOwner);
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
            fileStatuses
        );
    }

    private ReceiptFileEntry toReceiptFileEntry(ReceiptFile file, Map<String, ParsedReceipt> fileStatuses) {
        ParsedReceipt status = fileStatuses.getOrDefault(file.name(), null);
        String updated = formatInstant(file.updated());

        String statusBadgeClass = status != null ? status.statusBadgeClass() : "bg-secondary-subtle text-secondary";
        String statusValue = status != null ? status.status() : null;
        String statusMessage = status != null ? status.statusMessage() : null;

        return new ReceiptFileEntry(
            file.name(),
            file.name(),
            file.formattedSize(),
            file.ownerDisplayName(),
            updated,
            file.contentType(),
            statusValue,
            statusMessage,
            statusBadgeClass
        );
    }

    private ParsedReceiptEntry toParsedReceiptEntry(ParsedReceipt parsed) {
        String displayName = parsed.displayName() != null ? parsed.displayName() : parsed.objectPath();
        String updatedAt = formatInstant(parsed.updatedAt());
        String detailsUrl = parsed.id() != null ? "/receipts/" + parsed.id() : null;

        return new ParsedReceiptEntry(
            parsed.id(),
            displayName,
            parsed.objectPath(),
            parsed.objectName(),
            parsed.storeName(),
            parsed.receiptDate(),
            parsed.totalAmount(),
            parsed.formattedTotalAmount(),
            updatedAt,
            parsed.status(),
            parsed.statusMessage(),
            parsed.statusBadgeClass(),
            detailsUrl
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
        Map<String, ParsedReceipt> fileStatuses
    ) {
    }

    private record ReceiptFileEntry(
        String objectName,
        String name,
        String formattedSize,
        String ownerDisplayName,
        String updated,
        String contentType,
        String status,
        String statusMessage,
        String statusBadgeClass
    ) {
    }

    private record ParsedReceiptEntry(
        String id,
        String displayName,
        String objectPath,
        String objectName,
        String storeName,
        String receiptDate,
        String totalAmount,
        String formattedTotalAmount,
        String updatedAt,
        String status,
        String statusMessage,
        String statusBadgeClass,
        String detailsUrl
    ) {
    }

    private record ReceiptDashboardResponse(
        boolean storageEnabled,
        String listingError,
        List<ReceiptFileEntry> files,
        boolean parsedReceiptsEnabled,
        String parsedListingError,
        List<ParsedReceiptEntry> parsedReceipts
    ) {
    }

    private record ReceiptClearResponse(String successMessage, String errorMessage) {
    }

    private record ClearOutcome(String successMessage, String errorMessage) {
    }

    @GetMapping("/receipts/{documentId}")
    public String viewParsedReceipt(@PathVariable("documentId") String documentId, Model model, Authentication authentication) {
        if (receiptExtractionService.isEmpty() || !receiptExtractionService.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed receipts are not available.");
        }

        ReceiptOwner currentOwner = resolveReceiptOwner(authentication);
        if (currentOwner == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found.");
        }

        ParsedReceipt receipt = receiptExtractionService.get().findById(documentId)
            .filter(parsed -> ReceiptOwnerMatcher.belongsToCurrentOwner(parsed.owner(), currentOwner))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found."));

        String displayName = receipt.displayName();
        model.addAttribute("pageTitle", displayName != null ? "Receipt: " + displayName : "Receipt details");
        model.addAttribute("receipt", receipt);
        return "receipt-detail";
    }

    @PostMapping("/receipts/upload")
    public String uploadReceipts(
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        RedirectAttributes redirectAttributes,
        Authentication authentication
    ) {
        if (receiptStorageService.isEmpty() || !receiptStorageService.get().isEnabled()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Receipt uploads are disabled. Configure Google Cloud Storage to enable this feature.");
            return "redirect:/receipts";
        }

        List<MultipartFile> sanitizedFiles = files == null ? List.of()
            : files.stream().filter(file -> file != null && !file.isEmpty()).toList();

        if (sanitizedFiles.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please choose at least one file to upload.");
            return "redirect:/receipts";
        }

        if (sanitizedFiles.size() > 10) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can upload up to 10 files at a time.");
            return "redirect:/receipts";
        }

        ReceiptOwner owner = resolveReceiptOwner(authentication);

        try {
            receiptStorageService.get().uploadFiles(sanitizedFiles, owner);
            int count = sanitizedFiles.size();
            redirectAttributes.addFlashAttribute("successMessage",
                "%d file%s uploaded successfully.".formatted(count, count == 1 ? "" : "s"));
        } catch (ReceiptStorageException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            LOGGER.error("Failed to upload receipts", ex);
        }

        return "redirect:/receipts";
    }

    @PostMapping("/receipts/clear")
    public String clearReceipts(RedirectAttributes redirectAttributes, Authentication authentication) {
        ReceiptOwner owner = resolveReceiptOwner(authentication);
        if (owner == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to determine the current user.");
            return "redirect:/receipts";
        }

        ClearOutcome outcome = clearReceiptData(owner);

        if (outcome.successMessage() != null) {
            redirectAttributes.addFlashAttribute("successMessage", outcome.successMessage());
        }

        if (outcome.errorMessage() != null) {
            redirectAttributes.addFlashAttribute("errorMessage", outcome.errorMessage());
        }

        return "redirect:/receipts";
    }

    @PostMapping(value = "/receipts/clear", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ReceiptClearResponse> clearReceiptsJson(Authentication authentication) {
        ReceiptOwner owner = resolveReceiptOwner(authentication);
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ReceiptClearResponse(null, "Unable to determine the current user."));
        }

        ClearOutcome outcome = clearReceiptData(owner);
        HttpStatus status = outcome.successMessage() == null && outcome.errorMessage() != null
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.OK;

        return ResponseEntity.status(status)
            .body(new ReceiptClearResponse(outcome.successMessage(), outcome.errorMessage()));
    }

    private ClearOutcome clearReceiptData(ReceiptOwner owner) {
        boolean storageEnabled = receiptStorageService.isPresent() && receiptStorageService.get().isEnabled();
        boolean parsedReceiptsEnabled = receiptExtractionService.isPresent() && receiptExtractionService.get().isEnabled();

        if (!storageEnabled && !parsedReceiptsEnabled) {
            return new ClearOutcome(null, "Receipt storage and parsing are disabled.");
        }

        List<String> successes = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (storageEnabled) {
            try {
                receiptStorageService.get().deleteReceiptsForOwner(owner);
                successes.add("uploaded receipts");
            } catch (ReceiptStorageException ex) {
                errors.add("Failed to clear uploaded receipts: " + ex.getMessage());
                LOGGER.error("Failed to clear uploaded receipts", ex);
            }
        }

        if (parsedReceiptsEnabled) {
            try {
                receiptExtractionService.get().deleteReceiptsForOwner(owner);
                successes.add("parsed receipt data");
            } catch (ReceiptExtractionAccessException ex) {
                errors.add("Failed to clear parsed receipt data: " + ex.getMessage());
                LOGGER.error("Failed to clear parsed receipts", ex);
            }
        }

        String successMessage = null;
        if (!successes.isEmpty()) {
            successMessage = "Cleared " + String.join(" and ", successes) + ".";
        }

        String errorMessage = null;
        if (!errors.isEmpty()) {
            errorMessage = String.join(" ", errors);
        }

        if (successMessage == null && errorMessage == null) {
            errorMessage = "No receipt data was cleared.";
        }

        return new ClearOutcome(successMessage, errorMessage);
    }

    private ReceiptOwner resolveReceiptOwner(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        String identifier = authentication.getName();
        String displayName = null;
        String email = null;
        Object principal = authentication.getPrincipal();

        if (principal instanceof FirestoreUserDetails firestoreUserDetails) {
            identifier = firestoreUserDetails.getId();
            displayName = firestoreUserDetails.getDisplayName();
            email = firestoreUserDetails.getUsername();
        } else if (principal instanceof OAuth2User oAuth2User) {
            displayName = readAttribute(oAuth2User, "name");
            email = readAttribute(oAuth2User, "email");
            String subject = readAttribute(oAuth2User, "sub");
            identifier = StringUtils.hasText(subject) ? subject : oAuth2User.getName();
            if (!StringUtils.hasText(displayName)) {
                displayName = StringUtils.hasText(email) ? email : authentication.getName();
            }
        } else if (principal instanceof UserDetails userDetails) {
            identifier = userDetails.getUsername();
            displayName = userDetails.getUsername();
            email = userDetails.getUsername();
        } else if (principal instanceof String stringPrincipal) {
            displayName = stringPrincipal;
        }

        if (!StringUtils.hasText(identifier)) {
            identifier = authentication.getName();
        }

        ReceiptOwner owner = new ReceiptOwner(identifier, displayName, email);
        return owner.hasValues() ? owner : null;
    }

    private String readAttribute(OAuth2User oAuth2User, String attributeName) {
        Object value = oAuth2User.getAttributes().get(attributeName);
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return stringValue;
        }
        return null;
    }

}

