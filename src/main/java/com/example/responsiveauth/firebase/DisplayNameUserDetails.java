package com.example.responsiveauth.firebase;

import org.springframework.security.core.userdetails.UserDetails;

/**
 * Minimal projection of a Firebase-backed {@link UserDetails} that exposes the user's
 * display name in addition to the information required by Spring Security.
 */
public interface DisplayNameUserDetails extends UserDetails {

    String getUid();

    String getDisplayName();
}
