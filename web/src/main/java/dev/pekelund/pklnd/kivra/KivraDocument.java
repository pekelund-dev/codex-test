package dev.pekelund.pklnd.kivra;

import java.time.LocalDate;

/**
 * Represents a document from Kivra.
 */
public record KivraDocument(
    String id,
    String title,
    String type,
    LocalDate date,
    String contentType,
    byte[] content
) {
    public boolean isPdf() {
        return "application/pdf".equals(contentType);
    }
}
