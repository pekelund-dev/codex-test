package dev.pekelund.responsiveauth.web;

import dev.pekelund.responsiveauth.firestore.FirestoreUserService;
import dev.pekelund.responsiveauth.firestore.UserRegistrationException;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final FirestoreUserService firestoreUserService;
    private final MessageSource messageSource;

    public AuthController(FirestoreUserService firestoreUserService, MessageSource messageSource) {
        this.firestoreUserService = firestoreUserService;
        this.messageSource = messageSource;
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        model.addAttribute("pageTitleKey", "page.register.title");
        model.addAttribute("registrationEnabled", firestoreUserService.isEnabled());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult bindingResult,
                           Model model) {
        model.addAttribute("pageTitleKey", "page.register.title");
        model.addAttribute("registrationEnabled", firestoreUserService.isEnabled());

        Locale locale = LocaleContextHolder.getLocale();

        if (!firestoreUserService.isEnabled()) {
            bindingResult.reject("registrationDisabled",
                messageSource.getMessage("register.disabled", null, locale));
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "passwordMismatch",
                messageSource.getMessage("registration.password.mismatch", null, locale));
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            firestoreUserService.registerUser(form);
        } catch (UserRegistrationException | IllegalStateException ex) {
            bindingResult.reject("registrationFailed",
                ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : messageSource.getMessage("registration.failed", null, locale));
            return "register";
        }

        return "redirect:/login?registered";
    }
}
