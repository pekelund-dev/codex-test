package com.example.responsiveauth.web;

import com.example.responsiveauth.firebase.FirebaseAuthService;
import com.example.responsiveauth.firebase.FirebaseRegistrationException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final FirebaseAuthService firebaseAuthService;

    public AuthController(FirebaseAuthService firebaseAuthService) {
        this.firebaseAuthService = firebaseAuthService;
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        model.addAttribute("pageTitle", "Create account");
        model.addAttribute("firebaseConfigured", firebaseAuthService.isEnabled());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult bindingResult,
                           Model model) {
        model.addAttribute("pageTitle", "Create account");
        model.addAttribute("firebaseConfigured", firebaseAuthService.isEnabled());

        if (!firebaseAuthService.isEnabled()) {
            bindingResult.reject("firebaseNotConfigured",
                "Registration is currently unavailable because Firebase is not configured.");
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "passwordMismatch", "Passwords do not match.");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            firebaseAuthService.registerUser(form);
        } catch (FirebaseRegistrationException ex) {
            bindingResult.reject("registrationFailed", ex.getMessage());
            return "register";
        } catch (IllegalStateException ex) {
            bindingResult.reject("registrationFailed", ex.getMessage());
            return "register";
        }

        return "redirect:/login?registered";
    }
}
