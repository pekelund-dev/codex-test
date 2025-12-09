package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.config.HeaderAwareCookieLocaleResolver;
import dev.pekelund.pklnd.firestore.FirestoreReadTracker;
import dev.pekelund.pklnd.firestore.FirestoreReadTotals;
import dev.pekelund.pklnd.firestore.FirestoreUserDetails;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.GitProperties;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@ControllerAdvice
public class CurrentUserAdvice {

    private final ObjectProvider<FirestoreReadTracker> firestoreReadTrackerProvider;
    private final FirestoreReadTotals firestoreReadTotals;
    private final ObjectProvider<GitProperties> gitPropertiesProvider;

    public CurrentUserAdvice(
        ObjectProvider<FirestoreReadTracker> firestoreReadTrackerProvider,
        FirestoreReadTotals firestoreReadTotals,
        ObjectProvider<GitProperties> gitPropertiesProvider
    ) {
        this.firestoreReadTrackerProvider = firestoreReadTrackerProvider;
        this.firestoreReadTotals = firestoreReadTotals;
        this.gitPropertiesProvider = gitPropertiesProvider;
    }

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
        } else if (principal instanceof FirestoreUserDetails firestoreUserDetails) {
            displayName = firestoreUserDetails.getDisplayName();
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

    @ModelAttribute("supportedLanguages")
    public List<LanguageOption> supportedLanguages(HttpServletRequest request) {
        String requestUri = request != null ? request.getRequestURI() : "/";
        Map<String, String[]> parameters = request != null
            ? new LinkedHashMap<>(request.getParameterMap())
            : new LinkedHashMap<>();

        parameters.remove(HeaderAwareCookieLocaleResolver.LANGUAGE_PARAMETER_NAME);

        List<LanguageOption> options = new ArrayList<>();
        options.add(buildOption("sv", "nav.language.swedish", requestUri, parameters));
        options.add(buildOption("en", "nav.language.english", requestUri, parameters));
        return options;
    }

    @ModelAttribute("firestoreReadTracker")
    public FirestoreReadTracker firestoreReadTracker() {
        return firestoreReadTrackerProvider.getIfAvailable();
    }

    @ModelAttribute("firestoreReadTotals")
    public FirestoreReadTotals firestoreReadTotals() {
        return firestoreReadTotals;
    }

    @ModelAttribute("gitMetadata")
    public GitMetadata gitMetadata() {
        GitProperties gitProperties = gitPropertiesProvider.getIfAvailable();
        if (gitProperties == null) {
            return GitMetadata.empty();
        }

        return new GitMetadata(gitProperties.getBranch(), gitProperties.getShortCommitId());
    }

    private LanguageOption buildOption(
        String code,
        String labelKey,
        String requestUri,
        Map<String, String[]> parameters
    ) {
        UriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath().path(requestUri);
        parameters.forEach((name, values) -> builder.queryParam(name, (Object[]) values));
        builder.queryParam(HeaderAwareCookieLocaleResolver.LANGUAGE_PARAMETER_NAME, code);
        String href = builder.build().toUriString();
        return new LanguageOption(code, labelKey, href);
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
