package dev.pekelund.pklnd.web.statistics;

import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.ItemTag;
import dev.pekelund.pklnd.firestore.FirestoreUserService;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.firestore.TagService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import dev.pekelund.pklnd.web.DashboardStatisticsService;
import dev.pekelund.pklnd.web.DashboardStatisticsService.DashboardStatistics;
import dev.pekelund.pklnd.web.ReceiptOwnerResolver;
import dev.pekelund.pklnd.web.TagStatisticsService;
import dev.pekelund.pklnd.web.TagStatisticsService.TagSummary;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StatisticsController {

    private final DashboardStatisticsService dashboardStatisticsService;
    private final TagService tagService;
    private final ItemCategorizationService itemCategorizationService;
    private final ReceiptExtractionService receiptExtractionService;
    private final TagStatisticsService tagStatisticsService;
    private final ReceiptOwnerResolver receiptOwnerResolver;
    private final FirestoreUserService firestoreUserService;

    public StatisticsController(DashboardStatisticsService dashboardStatisticsService,
                                TagService tagService,
                                ItemCategorizationService itemCategorizationService,
                                ReceiptExtractionService receiptExtractionService,
                                TagStatisticsService tagStatisticsService,
                                ReceiptOwnerResolver receiptOwnerResolver,
                                FirestoreUserService firestoreUserService) {
        this.dashboardStatisticsService = dashboardStatisticsService;
        this.tagService = tagService;
        this.itemCategorizationService = itemCategorizationService;
        this.receiptExtractionService = receiptExtractionService;
        this.tagStatisticsService = tagStatisticsService;
        this.receiptOwnerResolver = receiptOwnerResolver;
        this.firestoreUserService = firestoreUserService;
    }

    @GetMapping("/dashboard")
    public String dashboardStatistics(Model model, Principal principal, Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.title");
        model.addAttribute("principalName", principal != null ? principal.getName() : "");
        model.addAttribute("admin", isAdmin(authentication));

        DashboardStatistics statistics = dashboardStatisticsService.loadStatistics(authentication);
        model.addAttribute("statistics", statistics);

        return "dashboard-statistics";
    }

    @GetMapping("/dashboard/statistics")
    public String legacyDashboardStatistics() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard/statistics/users")
    public String statisticsUsers(Model model, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return "redirect:/dashboard";
        }

        model.addAttribute("pageTitleKey", "page.statistics.users.title");
        model.addAttribute("userAccounts", firestoreUserService.listUserAccounts());
        return "statistics-users";
    }

    @GetMapping("/dashboard/statistics/stores")
    public String statisticsStores(Model model, Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.stores.title");

        List<DashboardStatisticsService.StoreStatistic> stores =
            dashboardStatisticsService.getStoreStatistics(authentication);
        model.addAttribute("stores", stores);

        return "statistics-stores";
    }

    @GetMapping("/dashboard/statistics/stores/{storeName}")
    public String statisticsStoreReceipts(@PathVariable String storeName,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          @RequestParam(required = false) String store,
                                          Model model,
                                          Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.store.receipts.title");
        model.addAttribute("storeName", storeName);

        model.addAttribute("filterStartDate", startDate != null ? startDate : "");
        model.addAttribute("filterEndDate", endDate != null ? endDate : "");
        model.addAttribute("filterStore", store != null ? store : "");
        model.addAttribute("hasFilters", startDate != null || endDate != null || store != null);

        List<ParsedReceipt> receipts =
            dashboardStatisticsService.getReceiptsForStore(storeName, authentication);

        receipts = dashboardStatisticsService.applyFilters(receipts, startDate, endDate, store);
        model.addAttribute("receipts", receipts);

        return "statistics-store-receipts";
    }

    @GetMapping("/dashboard/statistics/items")
    public String statisticsItems(Model model, Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.items.title");
        return "redirect:/receipts/overview";
    }

    @GetMapping("/dashboard/statistics/year/{year}")
    public String statisticsYear(@PathVariable int year,
                                 Model model,
                                 Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.year.title");
        model.addAttribute("year", year);

        model.addAttribute("prevYear", year - 1);
        model.addAttribute("nextYear", year + 1);

        List<ParsedReceipt> receipts =
            dashboardStatisticsService.getReceiptsForYear(year, authentication);

        model.addAttribute("receipts", receipts);

        BigDecimal totalDiscounts = dashboardStatisticsService.calculateTotalDiscounts(receipts);
        model.addAttribute("totalDiscounts", totalDiscounts);
        model.addAttribute("formattedTotalDiscounts", dashboardStatisticsService.formatDiscountAmount(totalDiscounts));

        return "statistics-year-receipts";
    }

    @GetMapping("/dashboard/statistics/year/{year}/month/{month}")
    public String statisticsYearMonth(@PathVariable int year,
                                      @PathVariable int month,
                                      Model model,
                                      Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.month.title");
        model.addAttribute("year", year);
        model.addAttribute("month", month);

        String monthName = resolveMonthName(month);
        model.addAttribute("monthName", monthName);

        populateMonthNavigation(year, month, model);

        List<ParsedReceipt> receipts =
            dashboardStatisticsService.getReceiptsForYearMonth(year, month, authentication);

        model.addAttribute("receipts", receipts);

        BigDecimal totalDiscounts = dashboardStatisticsService.calculateTotalDiscounts(receipts);
        model.addAttribute("totalDiscounts", totalDiscounts);
        model.addAttribute("formattedTotalDiscounts", dashboardStatisticsService.formatDiscountAmount(totalDiscounts));

        return "statistics-month-receipts";
    }

    @GetMapping("/dashboard/statistics/tags")
    public String statisticsTags(Model model, Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.tags.title");

        List<ItemTag> tags = tagService.listTags();
        model.addAttribute("tags", tags);
        model.addAttribute("tagsAvailable", !tags.isEmpty());
        Map<String, TagSummary> summaries = tagStatisticsService.summarizeTags(tags, authentication);
        if (summaries.isEmpty()) {
            summaries = tags.stream()
                .filter(tag -> tag != null && tag.id() != null)
                .collect(Collectors.toMap(ItemTag::id, tag -> TagSummary.empty()));
        }
        model.addAttribute("tagSummaries", summaries);

        return "statistics-tags";
    }

    @GetMapping("/dashboard/statistics/tags/{tagId}")
    public String statisticsTagItems(@PathVariable String tagId,
                                     Model model,
                                     Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.tag.items.title");

        ItemTag tag = tagService.findById(tagId).orElse(null);
        if (tag == null) {
            return "redirect:/dashboard/statistics/tags";
        }

        model.addAttribute("tag", tag);

        ReceiptOwner owner = receiptOwnerResolver.resolve(authentication);
        List<ItemCategorizationService.TaggedItemInfo> taggedItems =
            owner == null || !StringUtils.hasText(owner.id())
                ? List.of()
                : itemCategorizationService.getItemsByTag(tagId, owner.id());

        Map<String, List<ItemCategorizationService.TaggedItemInfo>> itemsByReceipt =
            taggedItems.stream()
                .collect(Collectors.groupingBy(ItemCategorizationService.TaggedItemInfo::receiptId));

        List<ReceiptWithTaggedItems> receiptsWithItems = itemsByReceipt.entrySet().stream()
            .map(entry -> {
                String receiptId = entry.getKey();
                List<ItemCategorizationService.TaggedItemInfo> items = entry.getValue();

                ParsedReceipt receipt = receiptExtractionService.findById(receiptId).orElse(null);

                return new ReceiptWithTaggedItems(receipt, items);
            })
            .filter(r -> r.receipt() != null)
            .sorted((a, b) -> {
                String dateA = a.receipt().receiptDate();
                String dateB = b.receipt().receiptDate();
                if (dateA == null || dateB == null) {
                    return 0;
                }
                return dateB.compareTo(dateA);
            })
            .collect(Collectors.toList());

        model.addAttribute("receiptsWithItems", receiptsWithItems);
        model.addAttribute("totalItems", taggedItems.size());

        return "statistics-tag-items";
    }

    public record ReceiptWithTaggedItems(
        ParsedReceipt receipt,
        List<ItemCategorizationService.TaggedItemInfo> taggedItems
    ) {}

    private String resolveMonthName(int month) {
        try {
            return Month.of(month).toString().toLowerCase();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void populateMonthNavigation(int year, int month, Model model) {
        try {
            YearMonth currentYearMonth = YearMonth.of(year, month);
            YearMonth previousYearMonth = currentYearMonth.minusMonths(1);
            YearMonth nextYearMonth = currentYearMonth.plusMonths(1);

            model.addAttribute("prevMonthYear", previousYearMonth.getYear());
            model.addAttribute("prevMonth", previousYearMonth.getMonthValue());
            model.addAttribute("nextMonthYear", nextYearMonth.getYear());
            model.addAttribute("nextMonth", nextYearMonth.getMonthValue());
        } catch (Exception e) {
            // Navigation not added when month/year is invalid.
        }
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
