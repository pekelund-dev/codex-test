package com.example.responsiveauth.firebase;

import com.example.responsiveauth.web.RegistrationForm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.NestedExceptionUtils;
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

    private static final String CERTIFICATE_SOURCE_UNAVAILABLE_EXCEPTION =
        "com.google.auth.mtls.CertificateSourceUnavailableException";

    private final FirebaseProperties properties;
    private final FirebaseAuth firebaseAuth;
    private final Firestore firestore;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public FirebaseAuthService(FirebaseProperties properties,
                               Optional<FirebaseApp> firebaseApp,
                               ObjectProvider<Firestore> firestoreProvider,
                               RestTemplate restTemplate) {
        this.properties = properties;
        this.firebaseAuth = firebaseApp.map(FirebaseAuth::getInstance).orElse(null);
        this.firestore = initializeFirestore(firestoreProvider);
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.enabled = properties.isEnabled()
            && firebaseApp.isPresent()
            && this.firestore != null
            && StringUtils.hasText(properties.getApiKey());

        if (!this.enabled) {
            if (properties.isEnabled()) {
                log.warn("Firebase integration is not fully configured. Registration and Firebase-backed login will be disabled.");
            } else {
                log.info("Firebase integration is disabled. Set firebase.enabled=true to activate it.");
            }
        }
    }

    private Firestore initializeFirestore(ObjectProvider<Firestore> firestoreProvider) {
        try {
            return firestoreProvider.getIfAvailable();
        } catch (BeansException ex) {
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
            if (isCertificateSourceUnavailable(rootCause)) {
                log.error("Failed to initialize Firestore because a Google Cloud mTLS client certificate could not be located. "
                        + "Unset GOOGLE_API_USE_CLIENT_CERTIFICATE or configure a valid client certificate to enable Firestore integration.",
                    rootCause);
            } else {
                log.error("Failed to initialize Firestore from the Firebase configuration", ex);
            }
            return null;
        }
    }

    private boolean isCertificateSourceUnavailable(Throwable rootCause) {
        return rootCause != null
            && CERTIFICATE_SOURCE_UNAVAILABLE_EXCEPTION.equals(rootCause.getClass().getName());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public FirebaseUserDetails registerUser(RegistrationForm registrationForm) {
        ensureEnabled();
        String normalizedEmail = normalizeEmail(registrationForm.getEmail());
        CreateRequest request = new CreateRequest()
            .setEmail(normalizedEmail)
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
        String defaultRole = StringUtils.hasText(properties.getDefaultRole())
            ? properties.getDefaultRole()
            : "ROLE_USER";
        Collection<SimpleGrantedAuthority> authorities =
            List.of(new SimpleGrantedAuthority(defaultRole));
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
            log.warn("Thread was interrupted while persisting user profile for uid={}", userRecord.getUid(), ex);
            Thread.currentThread().interrupt();
            throw new FirebaseRegistrationException("Registration was interrupted", ex);
        } catch (ExecutionException ex) {
            throw new FirebaseRegistrationException("Failed to persist the user profile in Firestore", ex);
        }
    }

    private FirebaseRegistrationException mapRegistrationException(FirebaseAuthException ex) {
        AuthErrorCode errorCode = ex.getAuthErrorCode();
        if (errorCode != null) {
            String message;
            switch (errorCode) {
                case EMAIL_ALREADY_EXISTS:
                    message = "An account with this email address already exists.";
                    break;
                case PHONE_NUMBER_ALREADY_EXISTS:
                    message = "An account with this phone number already exists.";
                    break;
                case UID_ALREADY_EXISTS:
                    message = "The provided user identifier is already in use.";
                    break;
                default:
                    message = "Failed to register user: " + errorCode.name();
                    break;
            }
            return new FirebaseRegistrationException(message, ex);
        }

        if (ex.getErrorCode() == ErrorCode.INVALID_ARGUMENT) {
            return new FirebaseRegistrationException("The provided registration data is invalid.", ex);
        }

        return new FirebaseRegistrationException("Failed to register user", ex);
    }

    private FirebaseSignInResponse signInWithPassword(String email, String password) {
        String apiKey = properties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Firebase API key is not configured");
        }

        String url = SIGN_IN_ENDPOINT + apiKey;
        String normalizedEmail = normalizeEmail(email);
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", normalizedEmail);
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

    private static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : null;
    }

    private static record FirebaseSignInResponse(String localId, String idToken, String refreshToken) {
    }
}
