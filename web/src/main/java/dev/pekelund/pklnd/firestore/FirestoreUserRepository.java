package dev.pekelund.pklnd.firestore;

import dev.pekelund.pklnd.web.RegistrationForm;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Pure Firestore CRUD and query operations, plus shared helpers used by sibling services.
 * Methods without an access modifier are package-private for use within the firestore package.
 */
@Service
public class FirestoreUserRepository {

    static final Logger log = LoggerFactory.getLogger(FirestoreUserRepository.class);

    final FirestoreProperties properties;
    private final Firestore firestore;
    private final FirestoreReadRecorder readRecorder;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public FirestoreUserRepository(
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
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long countUsers() {
        if (!enabled) {
            return countFallbackUsers();
        }

        try {
            CollectionReference collection = firestore.collection(properties.getUsersCollection());
            ApiFuture<QuerySnapshot> query = collection.get();
            QuerySnapshot snapshot = query.get();
            long readUnits = snapshot != null ? snapshot.size() : 0L;
            recordRead("Count users in " + properties.getUsersCollection(), readUnits);
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

    // --- Package-private query methods for sibling services ---

    DocumentSnapshot findUserByEmail(String normalizedEmail)
        throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(properties.getUsersCollection());
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

    List<QueryDocumentSnapshot> queryAdministratorDocuments() {
        ensureEnabled();
        String adminRole = adminRole();
        try {
            CollectionReference collection = firestore.collection(properties.getUsersCollection());
            ApiFuture<QuerySnapshot> queryFuture = collection.whereArrayContains("roles", adminRole).get();
            QuerySnapshot querySnapshot = queryFuture.get();
            recordRead("List administrator accounts", querySnapshot != null ? querySnapshot.size() : 0L);
            if (querySnapshot == null || querySnapshot.isEmpty()) {
                return List.of();
            }
            return querySnapshot.getDocuments();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UserRoleUpdateException("Interrupted while loading administrator accounts.", ex);
        } catch (ExecutionException ex) {
            throw new UserRoleUpdateException("Failed to load administrator accounts from Firestore.", ex);
        }
    }

    List<QueryDocumentSnapshot> queryAllUserDocuments() {
        try {
            CollectionReference collection = firestore.collection(properties.getUsersCollection());
            ApiFuture<QuerySnapshot> queryFuture = collection.get();
            QuerySnapshot querySnapshot = queryFuture.get();
            recordRead("List user accounts", querySnapshot != null ? querySnapshot.size() : 0L);
            if (querySnapshot == null || querySnapshot.isEmpty()) {
                return List.of();
            }
            return querySnapshot.getDocuments();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UserRoleUpdateException("Interrupted while loading user accounts.", ex);
        } catch (ExecutionException ex) {
            throw new UserRoleUpdateException("Failed to load user accounts from Firestore.", ex);
        }
    }

    DocumentReference addToUsersCollection(Map<String, Object> document)
        throws ExecutionException, InterruptedException {
        return firestore.collection(properties.getUsersCollection()).add(document).get();
    }

    void updateDocument(DocumentSnapshot doc, Map<String, Object> updates)
        throws ExecutionException, InterruptedException {
        doc.getReference().update(updates).get();
    }

    void updateDocumentField(DocumentSnapshot doc, String field, Object value)
        throws ExecutionException, InterruptedException {
        doc.getReference().update(field, value).get();
    }

    // --- Package-private helpers for sibling services ---

    String defaultRole() {
        String role = properties.getDefaultRole();
        if (!StringUtils.hasText(role)) {
            role = "ROLE_USER";
        }
        return ensureRolePrefix(role.trim());
    }

    String adminRole() {
        return ensureRolePrefix("ROLE_ADMIN");
    }

    static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : null;
    }

    String ensureRolePrefix(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Firestore integration is not configured. Unable to register users.");
        }
    }

    void recordRead(String description, long readUnits) {
        readRecorder.record(description, readUnits);
    }

    Collection<SimpleGrantedAuthority> defaultAuthorities() {
        return List.of(new SimpleGrantedAuthority(defaultRole()));
    }

    List<String> readRoleNames(DocumentSnapshot documentSnapshot) {
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

    Collection<SimpleGrantedAuthority> authoritiesFromRoles(List<String> roles) {
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

    List<String> normalizeRoleList(List<String> roles) {
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

    long countFallbackUsers() {
        List<FirestoreProperties.FallbackUser> fallbackUsers = properties.getFallbackUsers();
        return fallbackUsers != null ? fallbackUsers.size() : 0L;
    }

    private boolean userExists(String normalizedEmail) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(properties.getUsersCollection());
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
}
