package dev.pekelund.pklnd.firestore;

import com.google.cloud.firestore.Firestore;

/**
 * Defines a Firestore migration that can update existing data without requiring a reset.
 */
public interface FirestoreMigration {

    /**
     * Unique, monotonically increasing version number.
     */
    int version();

    /**
     * Short description of the migration's purpose.
     */
    String description();

    /**
     * Apply the migration using the provided Firestore client.
     */
    void apply(Firestore firestore) throws Exception;
}
