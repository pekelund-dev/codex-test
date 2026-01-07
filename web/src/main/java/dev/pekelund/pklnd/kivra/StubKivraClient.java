package dev.pekelund.pklnd.kivra;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of KivraClient for demonstration and testing purposes.
 * This implementation simulates the Kivra integration without making actual API calls.
 * 
 * A production implementation would:
 * 1. Use Kivra's authentication flow (BankID QR code or Mobile BankID)
 * 2. Make HTTP requests to Kivra's API endpoints
 * 3. Handle session management and token refresh
 * 4. Parse and download actual documents from Kivra mailbox
 * 
 * Based on open-source tools like kivra-sync (https://github.com/felixandersen/kivra-sync),
 * which demonstrate how to authenticate and fetch documents from Kivra.
 */
@Component
@ConditionalOnProperty(prefix = "kivra", name = "enabled", havingValue = "true")
public class StubKivraClient implements KivraClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(StubKivraClient.class);

    private final KivraProperties properties;
    private boolean authenticated = false;

    public StubKivraClient(KivraProperties properties) {
        this.properties = properties;
        LOGGER.info("Stub Kivra client initialized. This is a demonstration implementation.");
    }

    @Override
    public KivraAuthenticationResult authenticate() throws KivraAuthenticationException {
        LOGGER.info("Attempting Kivra authentication for user: {}", 
            maskPersonalNumber(properties.getPersonalNumber()));
        
        // In a real implementation, this would:
        // 1. Initiate BankID authentication
        // 2. Generate and return QR code data
        // 3. Poll for authentication status
        // 4. Store session tokens
        
        // For demonstration, we simulate successful authentication
        authenticated = true;
        return KivraAuthenticationResult.success("Autentiserad (demonstration)");
    }

    @Override
    public List<KivraDocument> fetchDocuments(int maxDocuments) throws KivraClientException {
        if (!authenticated) {
            throw new KivraClientException("Inte autentiserad. Kör autentisering först.");
        }

        LOGGER.info("Fetching up to {} documents from Kivra (stub mode)", maxDocuments);
        
        // In a real implementation, this would:
        // 1. Make authenticated HTTP requests to Kivra API
        // 2. Parse the response to get list of documents
        // 3. Download PDF content for each receipt
        // 4. Handle pagination and rate limiting
        
        // For demonstration, return empty list
        List<KivraDocument> documents = new ArrayList<>();
        
        LOGGER.info("Stub implementation - no actual documents fetched. " +
            "Integrate with Kivra API or use tools like kivra-sync for production use.");
        
        return documents;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void clearAuthentication() {
        authenticated = false;
        LOGGER.info("Kivra authentication cleared");
    }

    private String maskPersonalNumber(String personalNumber) {
        if (personalNumber == null || personalNumber.length() < 4) {
            return "****";
        }
        return personalNumber.substring(0, 6) + "****";
    }
}
