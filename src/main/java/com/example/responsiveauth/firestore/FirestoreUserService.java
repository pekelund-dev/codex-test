package com.example.responsiveauth.firestore;

import com.example.responsiveauth.web.RegistrationForm;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FirestoreUserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreUserService.class);

    private final FirestoreProperties properties;
    private final Firestore firestore;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final UserDetailsService fallbackUserDetailsService;

    public FirestoreUserService(FirestoreProperties properties,
                                ObjectProvider<Firestore> firestoreProvider,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.firestore = firestoreProvider.getIfAvailable();
        this.passwordEncoder = passwordEncoder;
        this.enabled = properties.isEnabled() && this.firestore != null;

        if (!this.enabled) {
            if (properties.isEnabled()) {
                log.warn("Firestore integration is enabled but Firestore could not be initialized. Registration is disabled.");
            } else {
                log.info("Firestore integration is disabled. Set firestore.enabled=true to persist users in Firestore.");
            }
            this.fallbackUserDetailsService = createFallbackUserDetailsService(passwordEncoder);
        } else {
            this.fallbackUserDetailsService = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public FirestoreUserDetails registerUser(RegistrationForm registrationForm) {
        ensureEnabled();

        String normalizedEmail = normalizeEmail(registrationForm.getEmail());
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new UserRegistrationException("Email address is required.");
        }

        String fullName = registrationForm.getFullName() != null
            ? registrationForm.getFullName().trim()
            : "";
        if (!StringUtils.hasText(fullName)) {
            throw new UserRegistrationException("Full name is required.");
        }

        try {
            if (userExists(normalizedEmail)) {
                throw new UserRegistrationException("An account with this email address already exists.");
            }

            String passwordHash = passwordEncoder.encode(registrationForm.getPassword());
            Map<String, Object> document = new HashMap<>();
            document.put("email", normalizedEmail);
            document.put("fullName", fullName);
            document.put("passwordHash", passwordHash);
            document.put("roles", List.of(defaultRole()));
            document.put("createdAt", FieldValue.serverTimestamp());

            CollectionReference usersCollection = firestore.collection(properties.getUsersCollection());
            ApiFuture<DocumentReference> writeFuture = usersCollection.add(document);
            DocumentReference documentReference = writeFuture.get();

            return new FirestoreUserDetails(
                documentReference.getId(),
                normalizedEmail,
                fullName,
                passwordHash,
                defaultAuthorities());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UserRegistrationException("Registration was interrupted.", ex);
        } catch (ExecutionException ex) {
            throw new UserRegistrationException("Failed to save the user in Firestore.", ex);
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = normalizeEmail(username);

        if (!StringUtils.hasText(normalizedEmail)) {
            throw new UsernameNotFoundException("Email address must be provided");
        }

        if (enabled) {
            try {
                DocumentSnapshot documentSnapshot = findUserByEmail(normalizedEmail);
                if (documentSnapshot == null) {
                    throw new UsernameNotFoundException(
                        "No user document returned for email " + normalizedEmail);
                }
                if (!documentSnapshot.exists()) {
                    throw new UsernameNotFoundException(
                        "No user document exists in Firestore for email " + normalizedEmail);
                }

                String passwordHash = documentSnapshot.getString("passwordHash");
                if (!StringUtils.hasText(passwordHash)) {
                    throw new UsernameNotFoundException(
                        "User record for " + normalizedEmail + " is missing a stored password hash.");
                }

                String fullName = documentSnapshot.getString("fullName");
                List<String> roles = readRoleNames(documentSnapshot);
                Collection<SimpleGrantedAuthority> authorities = authoritiesFromRoles(roles);

                return new FirestoreUserDetails(
                    documentSnapshot.getId(),
                    normalizedEmail,
                    StringUtils.hasText(fullName) ? fullName : normalizedEmail,
                    passwordHash,
                    authorities);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new UsernameNotFoundException("User lookup was interrupted", ex);
            } catch (ExecutionException ex) {
                throw new UsernameNotFoundException("Failed to query Firestore for user", ex);
            }
        }

        if (fallbackUserDetailsService != null) {
            return fallbackUserDetailsService.loadUserByUsername(username);
        }

        throw new UsernameNotFoundException("No user found with email " + normalizedEmail);
    }

    private boolean userExists(String normalizedEmail) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(properties.getUsersCollection());
        ApiFuture<QuerySnapshot> queryFuture = collection
            .whereEqualTo("email", normalizedEmail)
            .limit(1)
            .get();
        QuerySnapshot querySnapshot = queryFuture.get();
        return querySnapshot != null && !querySnapshot.isEmpty();
    }

    private DocumentSnapshot findUserByEmail(String normalizedEmail)
        throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(properties.getUsersCollection());
        ApiFuture<QuerySnapshot> queryFuture = collection
            .whereEqualTo("email", normalizedEmail)
            .limit(1)
            .get();
        QuerySnapshot querySnapshot = queryFuture.get();
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            return null;
        }
        return querySnapshot.getDocuments().get(0);
    }

    private Collection<SimpleGrantedAuthority> defaultAuthorities() {
        return List.of(new SimpleGrantedAuthority(defaultRole()));
    }

    private List<String> readRoleNames(DocumentSnapshot documentSnapshot) {
        List<?> storedRoles = documentSnapshot.get("roles", List.class);
        if (storedRoles == null || storedRoles.isEmpty()) {
            return List.of();
        }

        List<String> roles = new ArrayList<>(storedRoles.size());
        for (Object role : storedRoles) {
            if (role != null) {
                roles.add(role.toString());
            }
        }
        return roles;
    }

    private Collection<SimpleGrantedAuthority> authoritiesFromRoles(List<String> roles) {
        if (roles == null) {
            return defaultAuthorities();
        }

        List<String> filteredRoles = roles.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .toList();

        if (filteredRoles.isEmpty()) {
            return defaultAuthorities();
        }

        return filteredRoles.stream()
            .map(this::ensureRolePrefix)
            .map(SimpleGrantedAuthority::new)
            .toList();
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

    private void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Firestore integration is not configured. Unable to register users.");
        }
    }

    private static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : null;
    }

    private UserDetailsService createFallbackUserDetailsService(PasswordEncoder passwordEncoder) {
        List<FirestoreProperties.FallbackUser> configuredFallbackUsers = properties.getFallbackUsers();
        if (configuredFallbackUsers == null || configuredFallbackUsers.isEmpty()) {
            log.info("Firestore integration is disabled and no fallback users are configured. "
                + "Configure 'firestore.fallback-users' to enable in-memory credentials.");
            return username -> { throw new UsernameNotFoundException("No fallback users configured."); };
        }

        InMemoryUserDetailsManager inMemoryManager = new InMemoryUserDetailsManager();
        boolean userAdded = false;

        for (FirestoreProperties.FallbackUser fallbackUser : configuredFallbackUsers) {
            if (fallbackUser == null) {
                continue;
            }

            String username = fallbackUser.getUsername();
            String password = fallbackUser.getPassword();

            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                log.warn("Skipping fallback user because username or password is empty.");
                continue;
            }

            List<String> roles = fallbackUser.getRoles();
            Collection<SimpleGrantedAuthority> authorities =
                (roles == null || roles.isEmpty()) ? defaultAuthorities() : authoritiesFromRoles(roles);

            inMemoryManager.createUser(User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .authorities(authorities)
                .build());
            userAdded = true;
        }

        if (!userAdded) {
            log.warn("No fallback user accounts could be created. "
                + "Ensure at least one configured user provides non-empty credentials; "
                + "see prior warnings for skipped entries.");
        }

        return inMemoryManager;
    }
}
