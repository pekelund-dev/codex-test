package dev.pekelund.pklnd.web;

public record GitMetadata(String branch, String commitId, String version) {

    private static final String UNKNOWN_BRANCH = "okänd gren";
    private static final String UNKNOWN_COMMIT = "okänt commit-ID";
    private static final String UNKNOWN_VERSION = "okänd version";

    public static GitMetadata empty() {
        return new GitMetadata(null, null, null);
    }

    public boolean available() {
        return hasText(branch) || hasText(commitId) || hasText(version);
    }

    public String branchOrPlaceholder() {
        return hasText(branch) ? branch : UNKNOWN_BRANCH;
    }

    public String commitOrPlaceholder() {
        return hasText(commitId) ? commitId : UNKNOWN_COMMIT;
    }

    public String versionOrPlaceholder() {
        return hasText(version) ? version : UNKNOWN_VERSION;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
