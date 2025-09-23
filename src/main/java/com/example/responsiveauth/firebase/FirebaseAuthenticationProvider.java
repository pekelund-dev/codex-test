package com.example.responsiveauth.firebase;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAuthenticationProvider implements AuthenticationProvider {

    private final FirebaseAuthService firebaseAuthService;

    public FirebaseAuthenticationProvider(FirebaseAuthService firebaseAuthService) {
        this.firebaseAuthService = firebaseAuthService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!firebaseAuthService.isEnabled()) {
            throw new InternalAuthenticationServiceException(
                "Firebase authentication is not configured. Enable it in application.yml.");
        }

        String email = authentication.getName();
        Object credentials = authentication.getCredentials();
        if (credentials == null) {
            throw new BadCredentialsException("Password must not be empty");
        }

        String password = credentials.toString();
        try {
            DisplayNameUserDetails userDetails = firebaseAuthService.authenticate(email, password);
            return UsernamePasswordAuthenticationToken.authenticated(
                userDetails, authentication.getCredentials(), userDetails.getAuthorities());
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            throw new InternalAuthenticationServiceException(ex.getMessage(), ex);
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("Invalid email or password", ex);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
