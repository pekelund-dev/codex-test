package dev.pekelund.pklnd.firestore;

import dev.pekelund.pklnd.web.RegistrationForm;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FirestoreUserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreUserService.class);

    private final FirestoreProperties properties;
    private final Firestore firestore;
    private final FirestoreReadRecorder readRecorder;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final UserDetailsService fallbackUserDetailsService;

    public FirestoreUserService(
        FirestoreProperties properties,
        ObjectProvider<Firestore> firestoreProvider,
        PasswordEncoder passwordEncoder,
        FirestoreReadRecorder readRecorder
    ) {
        this.properties = properties;
        this.firestore = firestoreProvider.getIfAvailable();
        this.readRecorder = readRecorder;
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

    private Firestore firestore() {
        return Objects.requireNonNull(firestore, "firestore");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long countUsers() {
        if (!enabled) {
            return countFallbackUsers();
        }

        try {
            CollectionReference collection = firestore().collection(usersCollection());
            ApiFuture<QuerySnapshot> query = collection.get();
            QuerySnapshot snapshot = query.get();
            long readUnits = snapshot != null ? snapshot.size() : 0L;
            recordRead("Count users in " + usersCollection(), readUnits);
            return readUnits;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while counting Firestore user documents.", ex);
            return countFallbackUsers();
        } catch (ExecutionException ex) {
            log.error("Failed to count Firestore user documents.", ex);
            return countFallbackUsers();
        }
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

            CollectionReference usersCollection = firestore().collection(usersCollection());
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

    public List<AdminUserSummary> listAdministrators() {
        ensureEnabled();

        String adminRole = adminRole();

        try {
            CollectionReference collection = firestore().collection(usersCollection());
            ApiFuture<QuerySnapshot> queryFuture = collection.whereArrayContains("roles", adminRole).get();
            QuerySnapshot querySnapshot = queryFuture.get();
            recordRead(
                "List administrator accounts",
                querySnapshot != null ? querySnapshot.size() : 0L
            );

            if (querySnapshot == null || querySnapshot.isEmpty()) {
                return List.of();
            }

            return querySnapshot.getDocuments().stream()
                .map(this::toAdminUserSummary)
                .sorted(Comparator.comparing(AdminUserSummary::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UserRoleUpdateException("Interrupted while loading administrator accounts.", ex);
        } catch (ExecutionException ex) {
            throw new UserRoleUpdateException("Failed to load administrator accounts from Firestore.", ex);
        }
    }

    public AdminPromotionOutcome promoteToAdministrator(String email, String displayName) {
        ensureEnabled();

        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new UserRoleUpdateException("Email address is required to grant administrator access.");
        }

        String trimmedDisplayName = StringUtils.hasText(displayName) ? displayName.trim() : null;
        String adminRole = adminRole();
        String defaultRole = defaultRole();

        try {
            DocumentSnapshot documentSnapshot = findUserByEmail(normalizedEmail);

            if (documentSnapshot == null) {
                Map<String, Object> document = new HashMap<>();
                document.put("email", normalizedEmail);
                if (StringUtils.hasText(trimmedDisplayName)) {
                    document.put("fullName", trimmedDisplayName);
                }
                document.put("roles", List.of(defaultRole, adminRole));
                document.put("createdAt", FieldValue.serverTimestamp());
                document.put("authProvider", "admin-dashboard");

                CollectionReference collection = firestore().collection(usersCollection());
                collection.add(document).get();
                return new AdminPromotionOutcome(true, true);
            }

            List<String> normalizedRoles = normalizeRoleList(readRoleNames(documentSnapshot));
            boolean defaultAdded = false;
            if (!normalizedRoles.contains(defaultRole)) {
                normalizedRoles.add(defaultRole);
                defaultAdded = true;
            }

            boolean adminAdded = false;
            if (!normalizedRoles.contains(adminRole)) {
                normalizedRoles.add(adminRole);
                adminAdded = true;
            }

            Map<String, Object> updates = new HashMap<>();
            if (defaultAdded || adminAdded) {
                updates.put("roles", normalizedRoles);
            }

            if (StringUtils.hasText(trimmedDisplayName)) {
                String normalizedStoredDisplayName = Optional.ofNullable(documentSnapshot.getString("fullName"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .orElse(null);
                if (!Objects.equals(trimmedDisplayName, normalizedStoredDisplayName)) {
                    updates.put("fullName", trimmedDisplayName);
                }
            }

            if (!updates.isEmpty()) {
                documentSnapshot.getReference().update(updates).get();
            }

            return new AdminPromotionOutcome(false, adminAdded);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UserRoleUpdateException(
                "Interrupted while granting administrator access to " + normalizedEmail + ".",
                ex
            );
        } catch (ExecutionException ex) {
            throw new UserRoleUpdateException(
                "Failed to update administrator access for " + normalizedEmail + ".",
                ex
            );
        }
    }

    public AdminDemotionOutcome revokeAdministrator(String email) {
        ensureEnabled();

        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new UserRoleUpdateException("Email address is required to revoke administrator access.");
        }

        String adminRole = adminRole();
        String defaultRole = defaultRole();

        try {
            DocumentSnapshot documentSnapshot = findUserByEmail(normalizedEmail);

            if (documentSnapshot == null) {
                return new AdminDemotionOutcome(false, false);
            }

            List<String> normalizedRoles = normalizeRoleList(readRoleNames(documentSnapshot));
            boolean rolesChanged = false;

            boolean adminRemoved = normalizedRoles.remove(adminRole);
            if (adminRemoved) {
                rolesChanged = true;
            }

            if (!normalizedRoles.contains(defaultRole)) {
                normalizedRoles.add(defaultRole);
                rolesChanged = true;
            }

            if (rolesChanged) {
                documentSnapshot.getReference().update("roles", normalizedRoles).get();
            }

            return new AdminDemotionOutcome(true, adminRemoved);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UserRoleUpdateException(
                "Interrupted while revoking administrator access for " + normalizedEmail + ".",
                ex
            );
        } catch (ExecutionException ex) {
            throw new UserRoleUpdateException(
                "Failed to update administrator access for " + normalizedEmail + ".",
                ex
            );
        }
    }

    private boolean userExists(String normalizedEmail) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore().collection(usersCollection());
        ApiFuture<QuerySnapshot> queryFuture = collection
            .whereEqualTo("email", normalizedEmail)
            .limit(1)
            .get();
        QuerySnapshot querySnapshot = queryFuture.get();
        recordRead(
            "Check if user exists: " + normalizedEmail,
            querySnapshot != null ? querySnapshot.size() : 0L
        );
        return querySnapshot != null && !querySnapshot.isEmpty();
    }

    private DocumentSnapshot findUserByEmail(String normalizedEmail)
        throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(usersCollection());
        ApiFuture<QuerySnapshot> queryFuture = collection
            .whereEqualTo("email", normalizedEmail)
            .limit(1)
            .get();
        QuerySnapshot querySnapshot = queryFuture.get();
        recordRead(
            "Find user by email: " + normalizedEmail,
            querySnapshot != null ? querySnapshot.size() : 0L
        );
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            return null;
        }
        return querySnapshot.getDocuments().get(0);
    }

    private Collection<SimpleGrantedAuthority> defaultAuthorities() {
        return List.of(new SimpleGrantedAuthority(defaultRole()));
    }

    private List<String> readRoleNames(DocumentSnapshot documentSnapshot) {
        Object storedRolesValue = documentSnapshot.get("roles");
        if (!(storedRolesValue instanceof List<?>)) {
            return List.of();
        }

        List<?> storedRoles = (List<?>) storedRolesValue;
        if (storedRoles.isEmpty()) {
            return List.of();
        }

        List<String> roleNames = new ArrayList<>(storedRoles.size());
        for (Object role : storedRoles) {
            if (role instanceof String roleName) {
                roleNames.add(roleName);
            } else if (role != null) {
                log.warn("Ignoring non-string role value {} stored for user", role);
            }
        }

        return roleNames.isEmpty() ? List.of() : roleNames;
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

    private List<String> normalizeRoleList(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> normalized = new ArrayList<>(roles.size());
        for (String role : roles) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String normalizedRole = ensureRolePrefix(role.trim());
            if (!normalized.contains(normalizedRole)) {
                normalized.add(normalizedRole);
            }
        }

        return normalized;
    }

    private String defaultRole() {
        String role = properties.getDefaultRole();
        if (!StringUtils.hasText(role)) {
            role = "ROLE_USER";
        }
        return ensureRolePrefix(role.trim());
    }

    private String adminRole() {
        return ensureRolePrefix("ROLE_ADMIN");
    }

    private long countFallbackUsers() {
        List<FirestoreProperties.FallbackUser> fallbackUsers = properties.getFallbackUsers();
        return fallbackUsers != null ? fallbackUsers.size() : 0L;
    }

    private String ensureRolePrefix(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Firestore integration is not configured. Unable to register users.");
        }
    }

    private void recordRead(String description, long readUnits) {
        readRecorder.record(description, readUnits);
    }

    private String usersCollection() {
        return Objects.requireNonNull(properties.getUsersCollection(), "usersCollection");
    }

    private static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : null;
    }

    private AdminUserSummary toAdminUserSummary(DocumentSnapshot documentSnapshot) {
        String email = documentSnapshot.getString("email");
        if (!StringUtils.hasText(email)) {
            email = documentSnapshot.getId();
        }

        String displayName = Optional.ofNullable(documentSnapshot.getString("fullName"))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .orElse(email);

        return new AdminUserSummary(documentSnapshot.getId(), email, displayName);
    }

    private UserDetailsService createFallbackUserDetailsService(PasswordEncoder passwordEncoder) {
        List<FirestoreProperties.FallbackUser> configuredFallbackUsers = properties.getFallbackUsers();
        if (configuredFallbackUsers == null || configuredFallbackUsers.isEmpty()) {
            log.info("Firestore integration is disabled and no fallback users are configured. "
                + "Configure 'firestore.fallback-users' to enable in-memory credentials.");
            return username -> {
                throw new UsernameNotFoundException("No fallback users configured.");
            };
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

    public record AdminPromotionOutcome(boolean userCreated, boolean adminRoleGranted) { }

    public record AdminDemotionOutcome(boolean userFound, boolean adminRoleRevoked) { }

    public record AdminUserSummary(String id, String email, String displayName) { }
}
