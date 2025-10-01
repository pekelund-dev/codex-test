package dev.pekelund.responsiveauth.firestore;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class FirestoreUserDetails implements UserDetails {

    private final String id;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final Collection<? extends GrantedAuthority> authorities;

    public FirestoreUserDetails(String id,
                                String email,
                                String displayName,
                                String passwordHash,
                                Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
