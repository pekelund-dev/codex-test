package dev.pekelund.pklnd.web.receipts;

import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionAccessException;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.firestore.SearchItemResult;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.web.ReceiptOwnerResolver;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ReceiptSearchController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptSearchController.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Optional<ReceiptExtractionService> receiptExtractionService;
    private final ReceiptOwnerResolver receiptOwnerResolver;
    private final ReceiptScopeHelper scopeHelper;

    public ReceiptSearchController(
        @Autowired(required = false) ReceiptExtractionService receiptExtractionService,
        ReceiptOwnerResolver receiptOwnerResolver,
        ReceiptScopeHelper scopeHelper
    ) {
        this.receiptExtractionService = Optional.ofNullable(receiptExtractionService);
        this.receiptOwnerResolver = receiptOwnerResolver;
        this.scopeHelper = scopeHelper;
    }

    @GetMapping("/receipts/search")
    public String searchReceipts(
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(value = "scope", required = false) String scopeParam,
        Model model,
        Authentication authentication
    ) {
        ReceiptViewScope scope = scopeHelper.resolveScope(scopeParam, authentication);
        boolean canViewAll = scopeHelper.isAdmin(authentication);
        boolean viewingAll = scopeHelper.isViewingAll(scope, authentication);

        model.addAttribute("pageTitleKey", "page.receipts.search.title");
        model.addAttribute("scopeParam", scopeHelper.toScopeParameter(scope));
        model.addAttribute("canViewAll", canViewAll);
        model.addAttribute("viewingAll", viewingAll);
        model.addAttribute("searchQuery", query != null ? query : "");

        boolean parsedReceiptsEnabled = receiptExtractionService.isPresent() && receiptExtractionService.get().isEnabled();
        model.addAttribute("parsedReceiptsEnabled", parsedReceiptsEnabled);

        if (!parsedReceiptsEnabled) {
            model.addAttribute("searchResults", List.of());
            model.addAttribute("searchItemResults", List.of());
            model.addAttribute("searchPerformed", false);
            return "receipt-search";
        }

        if (StringUtils.hasText(query)) {
            ReceiptOwner currentOwner = receiptOwnerResolver.resolve(authentication);
            if (currentOwner == null && !viewingAll) {
                model.addAttribute("searchResults", List.of());
                model.addAttribute("searchItemResults", List.of());
                model.addAttribute("searchPerformed", true);
                return "receipt-search";
            }

            try {
                List<SearchItemResult> itemResults = receiptExtractionService.get()
                    .searchItemsByName(query, currentOwner, viewingAll);
                model.addAttribute("searchItemResults", itemResults);

                List<ParsedReceipt> results = receiptExtractionService.get()
                    .searchByItemName(query, currentOwner, viewingAll);
                List<ParsedReceiptEntry> entries = results.stream()
                    .map(this::toParsedReceiptEntry)
                    .toList();
                model.addAttribute("searchResults", entries);
                model.addAttribute("searchPerformed", true);
            } catch (ReceiptExtractionAccessException ex) {
                LOGGER.warn("Failed to search receipts", ex);
                model.addAttribute("searchResults", List.of());
                model.addAttribute("searchItemResults", List.of());
                model.addAttribute("searchPerformed", true);
                model.addAttribute("errorMessage", ex.getMessage());
            }
        } else {
            model.addAttribute("searchResults", List.of());
            model.addAttribute("searchItemResults", List.of());
            model.addAttribute("searchPerformed", false);
        }

        return "receipt-search";
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
            parsed.reconciliationStatus(),
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
        String reconciliationStatus,
        String status,
        String statusMessage,
        String statusBadgeClass,
        String detailsUrl
    ) {
    }
}
