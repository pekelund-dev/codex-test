package com.example.responsiveauth.firebase;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;

public class FirebaseUserDetails implements DisplayNameUserDetails {

    private final String uid;
    private final String email;
    private final String displayName;
    private final Collection<? extends GrantedAuthority> authorities;

    public FirebaseUserDetails(String uid, String email, String displayName,
                               Collection<? extends GrantedAuthority> authorities) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    @Override
    public String getUid() {
        return uid;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
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
