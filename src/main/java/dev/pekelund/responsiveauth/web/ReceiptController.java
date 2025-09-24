package dev.pekelund.responsiveauth.web;

import dev.pekelund.responsiveauth.firestore.FirestoreUserDetails;
import dev.pekelund.responsiveauth.storage.ReceiptFile;
import dev.pekelund.responsiveauth.storage.ReceiptOwner;
import dev.pekelund.responsiveauth.storage.ReceiptStorageException;
import dev.pekelund.responsiveauth.storage.ReceiptStorageService;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ReceiptController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptController.class);

    private final ReceiptStorageService receiptStorageService;

    public ReceiptController(ReceiptStorageService receiptStorageService) {
        this.receiptStorageService = receiptStorageService;
    }

    @GetMapping("/receipts")
    public String receipts(Model model, Authentication authentication) {
        model.addAttribute("pageTitle", "Receipts");
        model.addAttribute("storageEnabled", receiptStorageService.isEnabled());

        List<ReceiptFile> receiptFiles = Collections.emptyList();
        String listingError = null;
        ReceiptOwner currentOwner = resolveReceiptOwner(authentication);

        if (receiptStorageService.isEnabled()) {
            try {
                receiptFiles = receiptStorageService.listReceipts();
            } catch (ReceiptStorageException ex) {
                listingError = ex.getMessage();
                LOGGER.warn("Failed to list receipt files", ex);
            }
        }

        if (currentOwner == null) {
            receiptFiles = List.of();
        } else {
            receiptFiles = receiptFiles.stream()
                .filter(file -> isOwnedByCurrentUser(file.owner(), currentOwner))
                .toList();
        }

        model.addAttribute("files", receiptFiles);
        model.addAttribute("listingError", listingError);
        return "receipts";
    }

    @PostMapping("/receipts/upload")
    public String uploadReceipts(
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        RedirectAttributes redirectAttributes,
        Authentication authentication
    ) {
        if (!receiptStorageService.isEnabled()) {
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
            receiptStorageService.uploadFiles(sanitizedFiles, owner);
            int count = sanitizedFiles.size();
            redirectAttributes.addFlashAttribute("successMessage",
                "%d file%s uploaded successfully.".formatted(count, count == 1 ? "" : "s"));
        } catch (ReceiptStorageException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            LOGGER.error("Failed to upload receipts", ex);
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

    private boolean isOwnedByCurrentUser(ReceiptOwner fileOwner, ReceiptOwner currentOwner) {
        if (fileOwner == null || currentOwner == null) {
            return false;
        }

        if (hasMatchingId(fileOwner, currentOwner)) {
            return true;
        }

        if (hasMatchingEmail(fileOwner, currentOwner)) {
            return true;
        }

        return hasMatchingDisplayName(fileOwner, currentOwner);
    }

    private boolean hasMatchingId(ReceiptOwner fileOwner, ReceiptOwner currentOwner) {
        return StringUtils.hasText(fileOwner.id())
            && StringUtils.hasText(currentOwner.id())
            && fileOwner.id().equals(currentOwner.id());
    }

    private boolean hasMatchingEmail(ReceiptOwner fileOwner, ReceiptOwner currentOwner) {
        return StringUtils.hasText(fileOwner.email())
            && StringUtils.hasText(currentOwner.email())
            && fileOwner.email().equalsIgnoreCase(currentOwner.email());
    }

    private boolean hasMatchingDisplayName(ReceiptOwner fileOwner, ReceiptOwner currentOwner) {
        return StringUtils.hasText(fileOwner.displayName())
            && StringUtils.hasText(currentOwner.displayName())
            && fileOwner.displayName().equalsIgnoreCase(currentOwner.displayName());
    }
}

