package com.example.responsiveauth.web;

import com.example.responsiveauth.firebase.FirebaseUserDetails;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("userProfile")
    public UserProfile userProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return UserProfile.anonymous();
        }

        String displayName = authentication.getName();
        String imageUrl = null;
        Object principal = authentication.getPrincipal();

        if (principal instanceof OAuth2User oAuth2User) {
            displayName = (String) oAuth2User.getAttributes().getOrDefault("name", displayName);
            if (displayName == null) {
                displayName = (String) oAuth2User.getAttributes().getOrDefault("email", authentication.getName());
            }
            imageUrl = (String) oAuth2User.getAttributes().getOrDefault("picture", null);
        } else if (principal instanceof FirebaseUserDetails firebaseUserDetails) {
            displayName = firebaseUserDetails.getDisplayName();
        } else if (principal instanceof UserDetails userDetails) {
            displayName = userDetails.getUsername();
        }

        String initials = deriveInitials(displayName);
        return new UserProfile(true, displayName, initials, imageUrl);
    }

    @ModelAttribute("currentRequestUri")
    public String currentRequestUri(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        Object forwarded = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (forwarded instanceof String forwardedUri && !forwardedUri.isBlank()) {
            return forwardedUri;
        }

        String requestUri = request.getRequestURI();
        return requestUri != null ? requestUri : "";
    }

    private String deriveInitials(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "U";
        }
        String[] parts = displayName.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0)));
            }
            if (builder.length() == 2) {
                break;
            }
        }
        if (builder.length() == 0) {
            builder.append(Character.toUpperCase(displayName.trim().charAt(0)));
        }
        return builder.toString();
    }
}
