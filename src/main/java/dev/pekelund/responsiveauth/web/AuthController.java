package dev.pekelund.responsiveauth.web;

import dev.pekelund.responsiveauth.firestore.FirestoreUserService;
import dev.pekelund.responsiveauth.firestore.UserRegistrationException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final FirestoreUserService firestoreUserService;

    public AuthController(FirestoreUserService firestoreUserService) {
        this.firestoreUserService = firestoreUserService;
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        model.addAttribute("pageTitle", "Create account");
        model.addAttribute("registrationEnabled", firestoreUserService.isEnabled());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult bindingResult,
                           Model model) {
        model.addAttribute("pageTitle", "Create account");
        model.addAttribute("registrationEnabled", firestoreUserService.isEnabled());

        if (!firestoreUserService.isEnabled()) {
            bindingResult.reject("registrationDisabled",
                "Registration is currently unavailable because Firestore is not configured.");
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "passwordMismatch", "Passwords do not match.");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            firestoreUserService.registerUser(form);
        } catch (UserRegistrationException ex) {
            bindingResult.reject("registrationFailed", ex.getMessage());
            return "register";
        } catch (IllegalStateException ex) {
            bindingResult.reject("registrationFailed", ex.getMessage());
            return "register";
        }

        return "redirect:/login?registered";
    }
}
