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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReceiptController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptController.class);

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
        model.addAttribute("pageTitle", "Receipts");
        model.addAttribute("storageEnabled", receiptStorageService.isPresent() && receiptStorageService.get().isEnabled());

        List<ReceiptFile> receiptFiles = List.of();
        String listingError = null;
        ReceiptOwner currentOwner = resolveReceiptOwner(authentication);

        boolean parsedReceiptsEnabled = receiptExtractionService.isPresent() && receiptExtractionService.get().isEnabled();
        List<ParsedReceipt> parsedReceipts = List.of();
        String parsedListingError = null;

        if (receiptStorageService.isPresent() && receiptStorageService.get().isEnabled()) {
            try {
                receiptFiles = receiptStorageService.get().listReceipts();
            } catch (ReceiptStorageException ex) {
                listingError = ex.getMessage();
                LOGGER.warn("Failed to list receipt files", ex);
            }
        }

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

        model.addAttribute("files", receiptFiles);
        model.addAttribute("listingError", listingError);
        model.addAttribute("parsedReceiptsEnabled", parsedReceiptsEnabled);
        model.addAttribute("parsedReceipts", parsedReceipts);
        model.addAttribute("parsedListingError", parsedListingError);
        return "receipts";
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

        boolean storageEnabled = receiptStorageService.isPresent() && receiptStorageService.get().isEnabled();
        boolean parsedReceiptsEnabled = receiptExtractionService.isPresent() && receiptExtractionService.get().isEnabled();

        if (!storageEnabled && !parsedReceiptsEnabled) {
            redirectAttributes.addFlashAttribute("errorMessage", "Receipt storage and parsing are disabled.");
            return "redirect:/receipts";
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

        if (!successes.isEmpty()) {
            String message = "Cleared " + String.join(" and ", successes) + ".";
            redirectAttributes.addFlashAttribute("successMessage", message);
        }

        if (!errors.isEmpty()) {
            String message = String.join(" ", errors);
            redirectAttributes.addFlashAttribute("errorMessage", message);
        }

        if (successes.isEmpty() && errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No receipt data was cleared.");
        }

        return "redirect:/receipts";
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

