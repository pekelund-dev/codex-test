package dev.pekelund.responsiveauth.web;

import dev.pekelund.responsiveauth.firestore.FirestoreUserService;
import dev.pekelund.responsiveauth.firestore.UserRoleUpdateException;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final FirestoreUserService firestoreUserService;

    public HomeController(FirestoreUserService firestoreUserService) {
        this.firestoreUserService = firestoreUserService;
    }

    @GetMapping({"/", "/home"})
    public String home(Model model) {
        model.addAttribute("pageTitle", "Home");
        model.addAttribute("welcomeMessage", "Experience secure and modern authentication with responsive design.");
        return "home";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("pageTitle", "About");
        return "about";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal, Authentication authentication) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("principalName", principal != null ? principal.getName() : "");

        boolean admin = isAdmin(authentication);
        boolean firestoreEnabled = firestoreUserService.isEnabled();

        model.addAttribute("showAdminManagement", admin);
        model.addAttribute("adminManagementActive", admin && firestoreEnabled);

        if (admin && !firestoreEnabled) {
            model.addAttribute("adminManagementDisabledMessage",
                "Configure Firestore integration to manage administrator accounts.");
        }

        if (admin && firestoreEnabled) {
            if (!model.containsAttribute("adminPromotionForm")) {
                model.addAttribute("adminPromotionForm", new AdminPromotionForm());
            }

            try {
                model.addAttribute("currentAdmins", firestoreUserService.listAdministrators());
            } catch (UserRoleUpdateException ex) {
                model.addAttribute("currentAdmins", List.of());
                model.addAttribute("adminLoadError", ex.getMessage());
            }
        } else if (admin) {
            model.addAttribute("currentAdmins", List.of());
        }

        return "dashboard";
    }

    @PostMapping("/dashboard/admins")
    public String promoteAdministrator(@Valid @ModelAttribute("adminPromotionForm") AdminPromotionForm form,
                                       BindingResult bindingResult,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        if (!isAdmin(authentication)) {
            redirectAttributes.addFlashAttribute("adminErrorMessage",
                "You do not have permission to manage administrator accounts.");
            return "redirect:/dashboard";
        }

        if (!firestoreUserService.isEnabled()) {
            redirectAttributes.addFlashAttribute("adminErrorMessage",
                "Firestore integration must be enabled to manage administrator accounts.");
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
                String message = outcome.userCreated()
                    ? "Created a new user record and granted administrator access to %s."
                    : "Granted administrator access to %s.";
                redirectAttributes.addFlashAttribute(
                    "adminSuccessMessage",
                    String.format(message, normalizedEmail)
                );
            } else {
                redirectAttributes.addFlashAttribute(
                    "adminInfoMessage",
                    normalizedEmail + " already has administrator access."
                );
            }
        } catch (UserRoleUpdateException ex) {
            redirectAttributes.addFlashAttribute("adminErrorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("adminPromotionForm", form);
        }

        return "redirect:/dashboard#admin-management";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "Sign in");
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
