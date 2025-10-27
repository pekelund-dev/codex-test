package dev.pekelund.pklnd.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FirestoreUserAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final Logger log = LoggerFactory.getLogger(FirestoreUserAuthoritiesMapper.class);

    private static final Set<String> DEFAULT_ADMIN_EMAILS = Set.of("pekelund.dev@gmail.com");

    private final FirestoreProperties properties;
    private final Firestore firestore;
    private final boolean firestoreEnabled;
    private final ObjectProvider<FirestoreReadTracker> readTrackerProvider;

    public FirestoreUserAuthoritiesMapper(FirestoreProperties properties,
                                          ObjectProvider<Firestore> firestoreProvider,
                                          ObjectProvider<FirestoreReadTracker> readTrackerProvider) {
        this.properties = properties;
        this.firestore = firestoreProvider.getIfAvailable();
        this.firestoreEnabled = properties.isEnabled() && this.firestore != null;
        this.readTrackerProvider = readTrackerProvider;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mappedAuthorities = new LinkedHashSet<>();
        if (authorities != null) {
            mappedAuthorities.addAll(authorities);
        }

        mappedAuthorities.add(new SimpleGrantedAuthority(defaultRole()));

        if (!firestoreEnabled) {
            return List.copyOf(mappedAuthorities);
        }

        String email = extractEmail(authorities);
        if (!StringUtils.hasText(email)) {
            return List.copyOf(mappedAuthorities);
        }

        String normalizedEmail = normalizeEmail(email);
        String displayName = extractDisplayName(authorities);
        boolean isDefaultAdmin = DEFAULT_ADMIN_EMAILS.contains(normalizedEmail);

        if (isDefaultAdmin) {
            mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        try {
            DocumentSnapshot userDocument = findUserDocument(normalizedEmail);
            List<String> storedRoles;

            if (userDocument == null) {
                storedRoles = createUserDocument(normalizedEmail, displayName, isDefaultAdmin);
            } else {
                storedRoles = readRoleNames(userDocument);
                if (!StringUtils.hasText(displayName)) {
                    displayName = userDocument.getString("fullName");
                } else if (!StringUtils.hasText(userDocument.getString("fullName"))) {
                    updateDisplayName(userDocument.getReference(), displayName);
                }

                if (storedRoles.isEmpty()) {
                    storedRoles = List.of(defaultRole());
                }

                if (isDefaultAdmin) {
                    storedRoles = ensureAdminRole(userDocument.getReference(), storedRoles);
                }
            }

            storedRoles.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(this::ensureRolePrefix)
                .map(SimpleGrantedAuthority::new)
                .forEach(mappedAuthorities::add);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while resolving Firestore roles for {}.", normalizedEmail, ex);
        } catch (ExecutionException ex) {
            log.error("Failed to resolve Firestore roles for {}.", normalizedEmail, ex);
        }

        return List.copyOf(mappedAuthorities);
    }

    private DocumentSnapshot findUserDocument(String normalizedEmail)
        throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(properties.getUsersCollection());
        ApiFuture<QuerySnapshot> queryFuture = collection
            .whereEqualTo("email", normalizedEmail)
            .limit(1)
            .get();
        recordRead("Load OAuth user " + normalizedEmail);
        QuerySnapshot querySnapshot = queryFuture.get();
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            return null;
        }
        return querySnapshot.getDocuments().get(0);
    }

    private List<String> createUserDocument(String normalizedEmail, String displayName, boolean assignAdmin)
        throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(properties.getUsersCollection());

        Map<String, Object> document = new java.util.HashMap<>();
        document.put("email", normalizedEmail);
        if (StringUtils.hasText(displayName)) {
            document.put("fullName", displayName.trim());
        }
        List<String> roles = new ArrayList<>();
        roles.add(defaultRole());
        if (assignAdmin) {
            String adminRole = adminRole();
            if (!roles.contains(adminRole)) {
                roles.add(adminRole);
            }
        }
        document.put("roles", roles);
        document.put("createdAt", FieldValue.serverTimestamp());
        document.put("authProvider", "oauth");

        ApiFuture<DocumentReference> writeFuture = collection.add(document);
        writeFuture.get();
        return List.copyOf(roles);
    }

    private void updateDisplayName(DocumentReference reference, String displayName) {
        if (!StringUtils.hasText(displayName)) {
            return;
        }

        try {
            reference.update("fullName", displayName.trim()).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while updating full name for Firestore user document {}.", reference.getId(), ex);
        } catch (ExecutionException ex) {
            log.debug("Failed to update Firestore display name for document {}: {}", reference.getId(), ex.getMessage());
        }
    }

    private void recordRead(String description) {
        FirestoreReadTracker tracker = readTrackerProvider.getIfAvailable();
        if (tracker != null) {
            tracker.recordRead(description);
        }
    }

    private List<String> readRoleNames(DocumentSnapshot documentSnapshot) {
        Object storedRolesValue = documentSnapshot.get("roles");
        if (!(storedRolesValue instanceof List<?> storedRoles)) {
            return List.of();
        }

        List<String> roleNames = new ArrayList<>(storedRoles.size());
        for (Object role : storedRoles) {
            if (role instanceof String roleName) {
                roleNames.add(roleName);
            } else if (role != null) {
                log.warn("Ignoring non-string role value {} stored for user {}", role, documentSnapshot.getId());
            }
        }

        return roleNames;
    }

    private String extractEmail(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return null;
        }

        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidcAuthority) {
                String email = extractEmailFromOidc(oidcAuthority);
                if (StringUtils.hasText(email)) {
                    return email;
                }
            }

            if (authority instanceof OAuth2UserAuthority oauthAuthority) {
                Object value = oauthAuthority.getAttributes().get("email");
                if (value instanceof String email && StringUtils.hasText(email)) {
                    return email;
                }
            }
        }

        return null;
    }

    private String extractEmailFromOidc(OidcUserAuthority authority) {
        OidcIdToken idToken = authority.getIdToken();
        if (idToken != null) {
            Object claim = idToken.getClaims().get("email");
            if (claim instanceof String claimString && StringUtils.hasText(claimString)) {
                return claimString;
            }
        }

        Object attribute = authority.getAttributes().get("email");
        if (attribute instanceof String email && StringUtils.hasText(email)) {
            return email;
        }

        return null;
    }

    private String extractDisplayName(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return null;
        }

        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidcAuthority) {
                String displayName = extractDisplayNameFromOidc(oidcAuthority);
                if (StringUtils.hasText(displayName)) {
                    return displayName;
                }
            }

            if (authority instanceof OAuth2UserAuthority oauthAuthority) {
                String displayName = extractDisplayNameFromAttributes(oauthAuthority.getAttributes());
                if (StringUtils.hasText(displayName)) {
                    return displayName;
                }
            }
        }

        return null;
    }

    private String extractDisplayNameFromOidc(OidcUserAuthority authority) {
        String attributeName = extractDisplayNameFromAttributes(authority.getAttributes());
        if (StringUtils.hasText(attributeName)) {
            return attributeName;
        }

        OidcIdToken idToken = authority.getIdToken();
        if (idToken != null) {
            Object claim = idToken.getClaims().get("name");
            if (claim instanceof String nameClaim && StringUtils.hasText(nameClaim)) {
                return nameClaim;
            }
        }

        return null;
    }

    private String extractDisplayNameFromAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        return firstNonEmpty(
            toStringAttribute(attributes.get("name")),
            toStringAttribute(attributes.get("full_name")),
            combineNames(attributes.get("given_name"), attributes.get("family_name")),
            toStringAttribute(attributes.get("nickname")),
            toStringAttribute(attributes.get("preferred_username"))
        );
    }

    private String combineNames(Object givenName, Object familyName) {
        String given = toStringAttribute(givenName);
        String family = toStringAttribute(familyName);
        if (StringUtils.hasText(given) && StringUtils.hasText(family)) {
            return (given + " " + family).trim();
        }
        return StringUtils.hasText(given) ? given : family;
    }

    private String toStringAttribute(Object value) {
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return stringValue;
        }
        return null;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String defaultRole() {
        String role = properties.getDefaultRole();
        if (!StringUtils.hasText(role)) {
            role = "ROLE_USER";
        }
        return ensureRolePrefix(role.trim());
    }

    private String ensureRolePrefix(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    private String adminRole() {
        return ensureRolePrefix("ROLE_ADMIN");
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : null;
    }

    private List<String> ensureAdminRole(DocumentReference reference, List<String> storedRoles)
        throws ExecutionException, InterruptedException {
        String adminRole = adminRole();
        if (storedRoles.contains(adminRole)) {
            return storedRoles;
        }

        List<String> updatedRoles = new ArrayList<>(storedRoles);
        updatedRoles.add(adminRole);
        reference.update("roles", updatedRoles).get();
        return List.copyOf(updatedRoles);
    }
}
