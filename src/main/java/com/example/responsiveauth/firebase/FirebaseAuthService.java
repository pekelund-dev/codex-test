package com.example.responsiveauth.firebase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);
    private static final String SIGN_IN_ENDPOINT =
        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";

    private final FirebaseProperties properties;
    private final FirebaseAuth firebaseAuth;
    private final Firestore firestore;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public FirebaseAuthService(FirebaseProperties properties,
                               Optional<FirebaseApp> firebaseApp,
                               Optional<Firestore> firestore,
                               RestTemplate restTemplate) {
        this.properties = properties;
        this.firebaseAuth = firebaseApp.map(FirebaseAuth::getInstance).orElse(null);
        this.firestore = firestore.orElse(null);
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.enabled = properties.isEnabled()
            && firebaseApp.isPresent()
            && firestore.isPresent()
            && StringUtils.hasText(properties.getApiKey());

        if (!this.enabled) {
            if (properties.isEnabled()) {
                log.warn("Firebase integration is not fully configured. Registration and Firebase-backed login will be disabled.");
            } else {
                log.info("Firebase integration is disabled. Set firebase.enabled=true to activate it.");
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public FirebaseUserDetails registerUser(RegistrationForm registrationForm) {
        ensureEnabled();
        CreateRequest request = new CreateRequest()
            .setEmail(registrationForm.getEmail().trim().toLowerCase())
            .setPassword(registrationForm.getPassword())
            .setDisplayName(registrationForm.getFullName().trim());
        try {
            UserRecord userRecord = firebaseAuth.createUser(request);
            persistUserProfile(userRecord, registrationForm);
            return toUserDetails(userRecord);
        } catch (FirebaseAuthException ex) {
            throw mapRegistrationException(ex);
        }
    }

    public FirebaseUserDetails authenticate(String email, String password) {
        ensureEnabled();
        FirebaseSignInResponse response = signInWithPassword(email, password);
        try {
            UserRecord userRecord = firebaseAuth.getUser(response.localId());
            return toUserDetails(userRecord);
        } catch (FirebaseAuthException ex) {
            throw new InternalAuthenticationServiceException("Failed to load Firebase user details", ex);
        }
    }

    private FirebaseUserDetails toUserDetails(UserRecord userRecord) {
        String displayName = userRecord.getDisplayName();
        if (!StringUtils.hasText(displayName)) {
            displayName = userRecord.getEmail();
        }
        Collection<SimpleGrantedAuthority> authorities =
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new FirebaseUserDetails(userRecord.getUid(), userRecord.getEmail(), displayName, authorities);
    }

    private void persistUserProfile(UserRecord userRecord, RegistrationForm registrationForm) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", userRecord.getUid());
        profile.put("fullName", registrationForm.getFullName());
        profile.put("email", userRecord.getEmail());
        profile.put("createdAt", FieldValue.serverTimestamp());

        DocumentReference documentReference = firestore
            .collection(properties.getUsersCollection())
            .document(userRecord.getUid());
        ApiFuture<WriteResult> writeFuture = documentReference.set(profile);
        try {
            writeFuture.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FirebaseRegistrationException("Registration was interrupted", ex);
        } catch (ExecutionException ex) {
            throw new FirebaseRegistrationException("Failed to persist the user profile in Firestore", ex);
        }
    }

    private FirebaseRegistrationException mapRegistrationException(FirebaseAuthException ex) {
        AuthErrorCode errorCode = ex.getAuthErrorCode();
        if (errorCode != null) {
            return switch (errorCode) {
                case EMAIL_ALREADY_EXISTS -> new FirebaseRegistrationException(
                    "An account with this email address already exists.", ex);
                case INVALID_PASSWORD -> new FirebaseRegistrationException(
                    "The provided password is invalid.", ex);
                default -> new FirebaseRegistrationException("Failed to register user: " + errorCode.name(), ex);
            };
        }
        return new FirebaseRegistrationException("Failed to register user", ex);
    }

    private FirebaseSignInResponse signInWithPassword(String email, String password) {
        String apiKey = properties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Firebase API key is not configured");
        }

        String url = SIGN_IN_ENDPOINT + apiKey;
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("returnSecureToken", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<FirebaseSignInResponse> response =
                restTemplate.postForEntity(url, entity, FirebaseSignInResponse.class);
            FirebaseSignInResponse body = response.getBody();
            if (body == null || !StringUtils.hasText(body.localId())) {
                throw new InternalAuthenticationServiceException("Firebase sign-in response did not include a localId");
            }
            return body;
        } catch (HttpClientErrorException ex) {
            String message = parseFirebaseError(ex);
            throw new BadCredentialsException(message, ex);
        }
    }

    private String parseFirebaseError(HttpClientErrorException ex) {
        try {
            String responseBody = ex.getResponseBodyAsString();
            if (!StringUtils.hasText(responseBody)) {
                return "Invalid email or password.";
            }
            JsonNode root = objectMapper.readTree(responseBody);
            String firebaseMessage = root.path("error").path("message").asText("");
            return switch (firebaseMessage) {
                case "EMAIL_NOT_FOUND", "INVALID_PASSWORD" -> "Invalid email or password.";
                case "USER_DISABLED" -> "This account has been disabled.";
                default -> "Authentication with Firebase failed.";
            };
        } catch (IOException ioe) {
            return "Invalid email or password.";
        }
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Firebase integration is not configured. Set firebase.enabled=true and provide credentials.");
        }
    }

    private record FirebaseSignInResponse(String localId, String idToken, String refreshToken) {
    }
}
