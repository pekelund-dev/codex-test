package dev.pekelund.pklnd.config;

import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication token representing an anonymous demo-mode user.
 * The session stores the demo user ID; this token is reconstructed on every request
 * by {@link DemoSessionFilter} so it is never persisted in the HTTP session.
 */
public class DemoAuthentication implements Authentication {

    private final ReceiptOwner demoOwner;

    public DemoAuthentication(ReceiptOwner demoOwner) {
        this.demoOwner = demoOwner;
    }

    public ReceiptOwner getDemoOwner() {
        return demoOwner;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return demoOwner;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {
        // immutable – demo authentication cannot be modified
    }

    @Override
    public String getName() {
        return demoOwner != null ? demoOwner.id() : "demo";
    }
}
