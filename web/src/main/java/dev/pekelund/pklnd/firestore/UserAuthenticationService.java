package dev.pekelund.pklnd.firestore;

import com.google.cloud.firestore.DocumentSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Handles user authentication: resolves {@link UserDetails} from Firestore or a
 * configured in-memory fallback when Firestore is unavailable.
 */
@Service
public class UserAuthenticationService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserAuthenticationService.class);

    private final FirestoreUserRepository repo;
    private final UserDetailsService fallbackUserDetailsService;

    public UserAuthenticationService(
        FirestoreUserRepository repo,
        FirestoreProperties properties,
        PasswordEncoder passwordEncoder
    ) {
        this.repo = repo;
        this.fallbackUserDetailsService = repo.isEnabled()
            ? null
            : createFallbackUserDetailsService(properties, passwordEncoder);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = FirestoreUserRepository.normalizeEmail(username);

        if (!StringUtils.hasText(normalizedEmail)) {
            throw new UsernameNotFoundException("Email address must be provided");
        }

        if (repo.isEnabled()) {
            try {
                DocumentSnapshot documentSnapshot = repo.findUserByEmail(normalizedEmail);
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
                List<String> roles = repo.readRoleNames(documentSnapshot);
                Collection<SimpleGrantedAuthority> authorities = repo.authoritiesFromRoles(roles);

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

    private UserDetailsService createFallbackUserDetailsService(
        FirestoreProperties properties,
        PasswordEncoder passwordEncoder
    ) {
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
                (roles == null || roles.isEmpty()) ? repo.defaultAuthorities() : repo.authoritiesFromRoles(roles);

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
