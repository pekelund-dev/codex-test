package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.firestore.FirestoreUserService;
import dev.pekelund.pklnd.firestore.UserRoleUpdateException;
import dev.pekelund.pklnd.web.DashboardStatisticsService.DashboardStatistics;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final FirestoreUserService firestoreUserService;
    private final DashboardStatisticsService dashboardStatisticsService;
    private final MessageSource messageSource;

    public HomeController(FirestoreUserService firestoreUserService,
                          DashboardStatisticsService dashboardStatisticsService,
                          MessageSource messageSource) {
        this.firestoreUserService = firestoreUserService;
        this.dashboardStatisticsService = dashboardStatisticsService;
        this.messageSource = messageSource;
    }

    @GetMapping({"/", "/home"})
    public String home(Model model) {
        model.addAttribute("pageTitleKey", "page.home.title");
        return "home";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("pageTitleKey", "page.about.title");
        return "about";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal, Authentication authentication) {
        Locale locale = LocaleContextHolder.getLocale();
        model.addAttribute("pageTitleKey", "page.dashboard.title");
        model.addAttribute("principalName", principal != null ? principal.getName() : "");

        boolean admin = isAdmin(authentication);
        boolean firestoreEnabled = firestoreUserService.isEnabled();

        model.addAttribute("showAdminManagement", admin);
        model.addAttribute("adminManagementActive", admin && firestoreEnabled);

        if (admin && !firestoreEnabled) {
            model.addAttribute("adminManagementDisabledMessage",
                messageSource.getMessage("dashboard.admin.disabled", null, locale));
        }

        if (admin && firestoreEnabled) {
            if (!model.containsAttribute("adminPromotionForm")) {
                model.addAttribute("adminPromotionForm", new AdminPromotionForm());
            }

            try {
                model.addAttribute("currentAdmins", firestoreUserService.listAdministrators());
            } catch (UserRoleUpdateException ex) {
                model.addAttribute("currentAdmins", List.of());
                model.addAttribute("adminLoadError",
                    messageSource.getMessage("dashboard.admin.error.load", null, locale));
            }
        } else if (admin) {
            model.addAttribute("currentAdmins", List.of());
        }

        return "dashboard";
    }

    @GetMapping("/dashboard/statistics")
    public String dashboardStatistics(Model model, Principal principal, Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.title");
        model.addAttribute("principalName", principal != null ? principal.getName() : "");

        DashboardStatistics statistics = dashboardStatisticsService.loadStatistics(authentication);
        model.addAttribute("statistics", statistics);

        return "dashboard-statistics";
    }

    @GetMapping("/dashboard/statistics/users")
    public String statisticsUsers(Model model, Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.users.title");
        // Redirect to receipts page with all users' receipts
        return "redirect:/receipts";
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
    public String statisticsStoreReceipts(@org.springframework.web.bind.annotation.PathVariable String storeName,
                                           @org.springframework.web.bind.annotation.RequestParam(required = false) String startDate,
                                           @org.springframework.web.bind.annotation.RequestParam(required = false) String endDate,
                                           @org.springframework.web.bind.annotation.RequestParam(required = false) String store,
                                           Model model,
                                           Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.store.receipts.title");
        model.addAttribute("storeName", storeName);
        
        // Add filter parameters to model
        model.addAttribute("filterStartDate", startDate != null ? startDate : "");
        model.addAttribute("filterEndDate", endDate != null ? endDate : "");
        model.addAttribute("filterStore", store != null ? store : "");
        model.addAttribute("hasFilters", startDate != null || endDate != null || store != null);
        
        List<dev.pekelund.pklnd.firestore.ParsedReceipt> receipts = 
            dashboardStatisticsService.getReceiptsForStore(storeName, authentication);
        
        // Apply additional filters
        receipts = dashboardStatisticsService.applyFilters(receipts, startDate, endDate, store);
        model.addAttribute("receipts", receipts);
        
        return "statistics-store-receipts";
    }

    @GetMapping("/dashboard/statistics/items")
    public String statisticsItems(Model model, Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.items.title");
        // Redirect to receipt overview page
        return "redirect:/receipts/overview";
    }

    @GetMapping("/dashboard/statistics/year/{year}")
    public String statisticsYear(@org.springframework.web.bind.annotation.PathVariable int year,
                                  Model model,
                                  Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.year.title");
        model.addAttribute("year", year);
        
        // Calculate previous and next year for navigation
        model.addAttribute("prevYear", year - 1);
        model.addAttribute("nextYear", year + 1);
        
        List<dev.pekelund.pklnd.firestore.ParsedReceipt> receipts = 
            dashboardStatisticsService.getReceiptsForYear(year, authentication);
        
        model.addAttribute("receipts", receipts);
        
        return "statistics-year-receipts";
    }

    @GetMapping("/dashboard/statistics/year/{year}/month/{month}")
    public String statisticsYearMonth(@org.springframework.web.bind.annotation.PathVariable int year,
                                       @org.springframework.web.bind.annotation.PathVariable int month,
                                       Model model,
                                       Authentication authentication) {
        model.addAttribute("pageTitleKey", "page.statistics.month.title");
        model.addAttribute("year", year);
        model.addAttribute("month", month);
        
        // Add month name for display
        try {
            String monthName = java.time.Month.of(month).toString().toLowerCase();
            model.addAttribute("monthName", monthName);
        } catch (Exception e) {
            model.addAttribute("monthName", "unknown");
        }
        
        // Calculate previous and next month for navigation
        try {
            java.time.YearMonth currentYearMonth = java.time.YearMonth.of(year, month);
            java.time.YearMonth previousYearMonth = currentYearMonth.minusMonths(1);
            java.time.YearMonth nextYearMonth = currentYearMonth.plusMonths(1);
            
            model.addAttribute("prevMonthYear", previousYearMonth.getYear());
            model.addAttribute("prevMonth", previousYearMonth.getMonthValue());
            model.addAttribute("nextMonthYear", nextYearMonth.getYear());
            model.addAttribute("nextMonth", nextYearMonth.getMonthValue());
        } catch (Exception e) {
            // If calculation fails, don't add navigation buttons
        }
        
        List<dev.pekelund.pklnd.firestore.ParsedReceipt> receipts = 
            dashboardStatisticsService.getReceiptsForYearMonth(year, month, authentication);
        
        model.addAttribute("receipts", receipts);
        
        return "statistics-month-receipts";
    }

    @PostMapping("/dashboard/admins")
    public String promoteAdministrator(@Valid @ModelAttribute("adminPromotionForm") AdminPromotionForm form,
                                       BindingResult bindingResult,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        if (!isAdmin(authentication)) {
            redirectAttributes.addFlashAttribute("adminErrorMessage",
                messageSource.getMessage("dashboard.admin.error.no-permission", null, LocaleContextHolder.getLocale()));
            return "redirect:/dashboard";
        }

        if (!firestoreUserService.isEnabled()) {
            redirectAttributes.addFlashAttribute("adminErrorMessage",
                messageSource.getMessage("dashboard.admin.error.firestore", null, LocaleContextHolder.getLocale()));
            return "redirect:/dashboard#admin-management";
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                "org.springframework.validation.BindingResult.adminPromotionForm", bindingResult);
            redirectAttributes.addFlashAttribute("adminPromotionForm", form);
            return "redirect:/dashboard#admin-management";
        }

        String submittedEmail = form.getEmail() != null ? form.getEmail().trim() : "";
        String normalizedEmail = submittedEmail.toLowerCase(Locale.ROOT);

        try {
            FirestoreUserService.AdminPromotionOutcome outcome =
                firestoreUserService.promoteToAdministrator(submittedEmail, form.getFullName());

            if (outcome.adminRoleGranted()) {
                String messageKey = outcome.userCreated()
                    ? "dashboard.admin.success.created"
                    : "dashboard.admin.success.granted";
                redirectAttributes.addFlashAttribute(
                    "adminSuccessMessage",
                    messageSource.getMessage(messageKey, new Object[]{normalizedEmail}, LocaleContextHolder.getLocale())
                );
            } else {
                redirectAttributes.addFlashAttribute(
                    "adminInfoMessage",
                    messageSource.getMessage(
                        "dashboard.admin.info.already",
                        new Object[]{normalizedEmail},
                        LocaleContextHolder.getLocale()
                    )
                );
            }
        } catch (UserRoleUpdateException ex) {
            redirectAttributes.addFlashAttribute(
                "adminErrorMessage",
                messageSource.getMessage("dashboard.admin.error.generic", null, LocaleContextHolder.getLocale())
            );
            redirectAttributes.addFlashAttribute("adminPromotionForm", form);
        }

        return "redirect:/dashboard#admin-management";
    }

    @PostMapping("/dashboard/admins/remove")
    public String revokeAdministrator(@RequestParam("email") String email,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        if (!isAdmin(authentication)) {
            redirectAttributes.addFlashAttribute(
                "adminErrorMessage",
                messageSource.getMessage("dashboard.admin.error.no-permission", null, LocaleContextHolder.getLocale())
            );
            return "redirect:/dashboard";
        }

        if (!firestoreUserService.isEnabled()) {
            redirectAttributes.addFlashAttribute(
                "adminErrorMessage",
                messageSource.getMessage("dashboard.admin.error.firestore", null, LocaleContextHolder.getLocale())
            );
            return "redirect:/dashboard#admin-management";
        }

        String submittedEmail = email != null ? email.trim() : "";
        String normalizedEmail = submittedEmail.toLowerCase(Locale.ROOT);

        try {
            FirestoreUserService.AdminDemotionOutcome outcome =
                firestoreUserService.revokeAdministrator(submittedEmail);

            if (!outcome.userFound()) {
                redirectAttributes.addFlashAttribute(
                    "adminInfoMessage",
                    messageSource.getMessage(
                        "dashboard.admin.info.not-found",
                        new Object[]{normalizedEmail},
                        LocaleContextHolder.getLocale()
                    )
                );
            } else if (outcome.adminRoleRevoked()) {
                redirectAttributes.addFlashAttribute(
                    "adminSuccessMessage",
                    messageSource.getMessage(
                        "dashboard.admin.success.removed",
                        new Object[]{normalizedEmail},
                        LocaleContextHolder.getLocale()
                    )
                );
            } else {
                redirectAttributes.addFlashAttribute(
                    "adminInfoMessage",
                    messageSource.getMessage(
                        "dashboard.admin.info.no-role",
                        new Object[]{normalizedEmail},
                        LocaleContextHolder.getLocale()
                    )
                );
            }
        } catch (UserRoleUpdateException ex) {
            redirectAttributes.addFlashAttribute(
                "adminErrorMessage",
                messageSource.getMessage("dashboard.admin.error.generic", null, LocaleContextHolder.getLocale())
            );
        }

        return "redirect:/dashboard#admin-management";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitleKey", "page.login.title");
        return "login";
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        if (authentication.getAuthorities() == null) {
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }

        return false;
    }
}
