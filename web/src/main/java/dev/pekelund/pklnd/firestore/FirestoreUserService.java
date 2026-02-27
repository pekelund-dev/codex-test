package dev.pekelund.pklnd.firestore;

import dev.pekelund.pklnd.web.RegistrationForm;
import com.google.cloud.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Facade that preserves the original public API while delegating to focused services:
 * {@link FirestoreUserRepository}, {@link UserAuthenticationService}, and
 * {@link AdminManagementService}.
 */
@Service
public class FirestoreUserService implements UserDetailsService {

    private final FirestoreUserRepository repo;
    private final UserAuthenticationService authService;
    private final AdminManagementService adminService;

    public FirestoreUserService(
        FirestoreUserRepository repo,
        UserAuthenticationService authService,
        AdminManagementService adminService
    ) {
        this.repo = repo;
        this.authService = authService;
        this.adminService = adminService;
    }

    public boolean isEnabled() {
        return repo.isEnabled();
    }

    public long countUsers() {
        return repo.countUsers();
    }

    public FirestoreUserDetails registerUser(RegistrationForm registrationForm) {
        return repo.registerUser(registrationForm);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return authService.loadUserByUsername(username);
    }

    public List<AdminUserSummary> listAdministrators() {
        return repo.queryAdministratorDocuments().stream()
            .map(this::toAdminUserSummary)
            .sorted(Comparator.comparing(AdminUserSummary::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public List<UserAccountSummary> listUserAccounts() {
        if (!repo.isEnabled()) {
            return listFallbackUserAccounts();
        }
        return repo.queryAllUserDocuments().stream()
            .map(this::toUserAccountSummary)
            .sorted(Comparator.comparing(UserAccountSummary::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public AdminPromotionOutcome promoteToAdministrator(String email, String displayName) {
        AdminManagementService.PromotionResult result = adminService.promoteToAdministrator(email, displayName);
        return new AdminPromotionOutcome(result.userCreated(), result.adminRoleGranted());
    }

    public AdminDemotionOutcome revokeAdministrator(String email) {
        AdminManagementService.DemotionResult result = adminService.revokeAdministrator(email);
        return new AdminDemotionOutcome(result.userFound(), result.adminRoleRevoked());
    }

    // --- Mapping helpers ---

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

    private UserAccountSummary toUserAccountSummary(DocumentSnapshot documentSnapshot) {
        String email = documentSnapshot.getString("email");
        if (!StringUtils.hasText(email)) {
            email = documentSnapshot.getId();
        }

        String displayName = Optional.ofNullable(documentSnapshot.getString("fullName"))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .orElse(email);

        List<String> roles = repo.normalizeRoleList(repo.readRoleNames(documentSnapshot));
        if (roles.isEmpty()) {
            roles = List.of(repo.defaultRole());
        }

        return new UserAccountSummary(
            documentSnapshot.getId(),
            email,
            displayName,
            roles,
            Optional.ofNullable(documentSnapshot.getString("authProvider")).orElse("firestore")
        );
    }

    private List<UserAccountSummary> listFallbackUserAccounts() {
        List<FirestoreProperties.FallbackUser> fallbackUsers = repo.properties.getFallbackUsers();
        if (fallbackUsers == null || fallbackUsers.isEmpty()) {
            return List.of();
        }

        List<UserAccountSummary> summaries = new ArrayList<>();
        for (int i = 0; i < fallbackUsers.size(); i++) {
            FirestoreProperties.FallbackUser fallbackUser = fallbackUsers.get(i);
            if (fallbackUser == null || !StringUtils.hasText(fallbackUser.getUsername())) {
                continue;
            }

            List<String> roles = fallbackUser.getRoles();
            List<String> normalizedRoles = (roles == null || roles.isEmpty())
                ? List.of(repo.defaultRole())
                : repo.normalizeRoleList(roles);
            if (normalizedRoles.isEmpty()) {
                normalizedRoles = List.of(repo.defaultRole());
            }

            String username = fallbackUser.getUsername().trim();
            summaries.add(new UserAccountSummary(
                "fallback-" + i,
                username,
                username,
                normalizedRoles,
                "fallback"
            ));
        }

        return summaries.stream()
            .sorted(Comparator.comparing(UserAccountSummary::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    // --- Public inner records (preserved for backward compatibility with callers) ---

    public record AdminPromotionOutcome(boolean userCreated, boolean adminRoleGranted) { }

    public record AdminDemotionOutcome(boolean userFound, boolean adminRoleRevoked) { }

    public record AdminUserSummary(String id, String email, String displayName) { }

    public record UserAccountSummary(String id, String email, String displayName, List<String> roles, String authProvider) { }
}
