package dev.pekelund.pklnd.firestore;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Handles administrator promotion and demotion operations.
 */
@Service
public class AdminManagementService {

    private final FirestoreUserRepository repo;

    public AdminManagementService(FirestoreUserRepository repo) {
        this.repo = repo;
    }

    /**
     * Promotes a user to administrator, creating the account if it does not yet exist.
     */
    public PromotionResult promoteToAdministrator(String email, String displayName) {
        repo.ensureEnabled();

        String normalizedEmail = FirestoreUserRepository.normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new UserRoleUpdateException("Email address is required to grant administrator access.");
        }

        String trimmedDisplayName = StringUtils.hasText(displayName) ? displayName.trim() : null;
        String adminRole = repo.adminRole();
        String defaultRole = repo.defaultRole();

        try {
            DocumentSnapshot documentSnapshot = repo.findUserByEmail(normalizedEmail);

            if (documentSnapshot == null) {
                Map<String, Object> document = new HashMap<>();
                document.put("email", normalizedEmail);
                if (StringUtils.hasText(trimmedDisplayName)) {
                    document.put("fullName", trimmedDisplayName);
                }
                document.put("roles", List.of(defaultRole, adminRole));
                document.put("createdAt", FieldValue.serverTimestamp());
                document.put("authProvider", "admin-dashboard");

                repo.addToUsersCollection(document);
                return new PromotionResult(true, true);
            }

            List<String> normalizedRoles = repo.normalizeRoleList(repo.readRoleNames(documentSnapshot));
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
                repo.updateDocument(documentSnapshot, updates);
            }

            return new PromotionResult(false, adminAdded);
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

    /**
     * Revokes administrator access from a user.
     */
    public DemotionResult revokeAdministrator(String email) {
        repo.ensureEnabled();

        String normalizedEmail = FirestoreUserRepository.normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new UserRoleUpdateException("Email address is required to revoke administrator access.");
        }

        String adminRole = repo.adminRole();
        String defaultRole = repo.defaultRole();

        try {
            DocumentSnapshot documentSnapshot = repo.findUserByEmail(normalizedEmail);

            if (documentSnapshot == null) {
                return new DemotionResult(false, false);
            }

            List<String> normalizedRoles = repo.normalizeRoleList(repo.readRoleNames(documentSnapshot));
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
                repo.updateDocumentField(documentSnapshot, "roles", normalizedRoles);
            }

            return new DemotionResult(true, adminRemoved);
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

    record PromotionResult(boolean userCreated, boolean adminRoleGranted) { }

    record DemotionResult(boolean userFound, boolean adminRoleRevoked) { }
}
