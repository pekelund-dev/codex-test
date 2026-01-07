package dev.pekelund.pklnd.kivra;

import java.util.List;

/**
 * Client for interacting with Kivra digital mailbox.
 */
public interface KivraClient {

    /**
     * Authenticate with Kivra using BankID.
     * 
     * @return Authentication result with session information
     * @throws KivraAuthenticationException if authentication fails
     */
    KivraAuthenticationResult authenticate() throws KivraAuthenticationException;

    /**
     * Fetch documents from Kivra mailbox.
     * 
     * @param maxDocuments Maximum number of documents to fetch
     * @return List of documents from Kivra
     * @throws KivraClientException if fetching documents fails
     */
    List<KivraDocument> fetchDocuments(int maxDocuments) throws KivraClientException;

    /**
     * Check if the client is authenticated and ready to fetch documents.
     * 
     * @return true if authenticated, false otherwise
     */
    boolean isAuthenticated();

    /**
     * Clear authentication state.
     */
    void clearAuthentication();
}
