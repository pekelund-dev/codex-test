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
