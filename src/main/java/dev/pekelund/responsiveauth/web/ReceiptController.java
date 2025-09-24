package dev.pekelund.responsiveauth.web;

import dev.pekelund.responsiveauth.storage.ReceiptFile;
import dev.pekelund.responsiveauth.storage.ReceiptStorageException;
import dev.pekelund.responsiveauth.storage.ReceiptStorageService;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    public String receipts(Model model) {
        model.addAttribute("pageTitle", "Receipts");
        model.addAttribute("storageEnabled", receiptStorageService.isEnabled());

        List<ReceiptFile> receiptFiles = Collections.emptyList();
        String listingError = null;

        if (receiptStorageService.isEnabled()) {
            try {
                receiptFiles = receiptStorageService.listReceipts();
            } catch (ReceiptStorageException ex) {
                listingError = ex.getMessage();
                LOGGER.warn("Failed to list receipt files", ex);
            }
        }

        model.addAttribute("files", receiptFiles);
        model.addAttribute("listingError", listingError);
        return "receipts";
    }

    @PostMapping("/receipts/upload")
    public String uploadReceipts(
        @RequestParam(value = "files", required = false) List<MultipartFile> files,
        RedirectAttributes redirectAttributes
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

        try {
            receiptStorageService.uploadFiles(sanitizedFiles);
            int count = sanitizedFiles.size();
            redirectAttributes.addFlashAttribute("successMessage",
                "%d file%s uploaded successfully.".formatted(count, count == 1 ? "" : "s"));
        } catch (ReceiptStorageException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            LOGGER.error("Failed to upload receipts", ex);
        }

        return "redirect:/receipts";
    }
}

