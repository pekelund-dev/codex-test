package dev.pekelund.responsiveauth.web;

import dev.pekelund.responsiveauth.firestore.FirestoreUserDetails;
import dev.pekelund.responsiveauth.storage.ReceiptOwner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReceiptOwnerResolver {

    public ReceiptOwner resolve(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        String identifier = authentication.getName();
        String displayName = null;
        String email = null;
        Object principal = authentication.getPrincipal();

        if (principal instanceof FirestoreUserDetails firestoreUserDetails) {
            identifier = firestoreUserDetails.getId();
            displayName = firestoreUserDetails.getDisplayName();
            email = firestoreUserDetails.getUsername();
        } else if (principal instanceof OAuth2User oAuth2User) {
            displayName = readAttribute(oAuth2User, "name");
            email = readAttribute(oAuth2User, "email");
            String subject = readAttribute(oAuth2User, "sub");
            identifier = StringUtils.hasText(subject) ? subject : oAuth2User.getName();
            if (!StringUtils.hasText(displayName)) {
                displayName = StringUtils.hasText(email) ? email : authentication.getName();
            }
        } else if (principal instanceof UserDetails userDetails) {
            identifier = userDetails.getUsername();
            displayName = userDetails.getUsername();
            email = userDetails.getUsername();
        } else if (principal instanceof String stringPrincipal) {
            displayName = stringPrincipal;
        }

        if (!StringUtils.hasText(identifier)) {
            identifier = authentication.getName();
        }

        ReceiptOwner owner = new ReceiptOwner(identifier, displayName, email);
        return owner.hasValues() ? owner : null;
    }

    private String readAttribute(OAuth2User oAuth2User, String attributeName) {
        Object value = oAuth2User.getAttributes().get(attributeName);
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return stringValue;
        }
        return null;
    }
}
