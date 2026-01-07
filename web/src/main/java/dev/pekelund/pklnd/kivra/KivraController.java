package dev.pekelund.pklnd.kivra;

import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.web.ReceiptOwnerResolver;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for Kivra integration operations.
 */
@Controller
@ConditionalOnProperty(prefix = "kivra", name = "enabled", havingValue = "true")
public class KivraController {

    private static final Logger LOGGER = LoggerFactory.getLogger(KivraController.class);

    private final Optional<KivraSyncService> kivraSyncService;
    private final ReceiptOwnerResolver receiptOwnerResolver;

    public KivraController(
            Optional<KivraSyncService> kivraSyncService,
            ReceiptOwnerResolver receiptOwnerResolver) {
        this.kivraSyncService = kivraSyncService;
        this.receiptOwnerResolver = receiptOwnerResolver;
    }

    /**
     * Check if Kivra sync is available.
     */
    @GetMapping(value = "/receipts/kivra/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<KivraStatusResponse> kivraStatus() {
        boolean available = kivraSyncService.isPresent() && kivraSyncService.get().isAvailable();
        String message = available
            ? "Kivra-synkronisering är tillgänglig"
            : "Kivra-synkronisering är inte konfigurerad";
        return ResponseEntity.ok(new KivraStatusResponse(available, message));
    }

    /**
     * Trigger Kivra sync (HTML form submission).
     */
    @PostMapping("/receipts/kivra/sync")
    public String syncFromKivra(
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kunde inte identifiera användare");
            return "redirect:/receipts/uploads";
        }

        if (kivraSyncService.isEmpty() || !kivraSyncService.get().isAvailable()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kivra-synkronisering är inte tillgänglig");
            return "redirect:/receipts/uploads";
        }

        KivraSyncResult result = kivraSyncService.get().syncReceipts(owner);

        if (result.success()) {
            redirectAttributes.addFlashAttribute("successMessage", result.message());
        } else if (result.authenticationPending()) {
            redirectAttributes.addFlashAttribute("kivraAuthPending", true);
            redirectAttributes.addFlashAttribute("kivraQrCode", result.qrCodeData());
            redirectAttributes.addFlashAttribute("infoMessage", result.message());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", result.message());
        }

        return "redirect:/receipts/uploads";
    }

    /**
     * Trigger Kivra sync (JSON API).
     */
    @PostMapping(value = "/receipts/kivra/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<KivraSyncResponse> syncFromKivraJson(Authentication authentication) {
        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        if (owner == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new KivraSyncResponse(false, false, null, 0, 0, "Kunde inte identifiera användare"));
        }

        if (kivraSyncService.isEmpty() || !kivraSyncService.get().isAvailable()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new KivraSyncResponse(false, false, null, 0, 0, "Kivra-synkronisering är inte tillgänglig"));
        }

        KivraSyncResult result = kivraSyncService.get().syncReceipts(owner);

        HttpStatus status = result.success() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
            .body(new KivraSyncResponse(
                result.success(),
                result.authenticationPending(),
                result.qrCodeData(),
                result.uploadedCount(),
                result.failureCount(),
                result.message()
            ));
    }

    /**
     * Response for Kivra status check.
     */
    private record KivraStatusResponse(boolean available, String message) {
    }

    /**
     * Response for Kivra sync operation.
     */
    private record KivraSyncResponse(
        boolean success,
        boolean authenticationPending,
        String qrCodeData,
        int uploadedCount,
        int failureCount,
        String message
    ) {
    }
}
