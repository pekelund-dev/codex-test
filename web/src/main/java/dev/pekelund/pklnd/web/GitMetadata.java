package dev.pekelund.pklnd.web;

public record GitMetadata(String branch, String commitId) {

    private static final String UNKNOWN_BRANCH = "okänd gren";
    private static final String UNKNOWN_COMMIT = "okänt commit-ID";

    public static GitMetadata empty() {
        return new GitMetadata(null, null);
    }

    public boolean available() {
        return hasText(branch) || hasText(commitId);
    }

    public String branchOrPlaceholder() {
        return hasText(branch) ? branch : UNKNOWN_BRANCH;
    }

    public String commitOrPlaceholder() {
        return hasText(commitId) ? commitId : UNKNOWN_COMMIT;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
