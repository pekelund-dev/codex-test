package dev.pekelund.pklnd.web;

public record GitMetadata(String branch, String commitId) {

    public static GitMetadata empty() {
        return new GitMetadata(null, null);
    }

    public boolean available() {
        return branch != null && !branch.isBlank()
            && commitId != null && !commitId.isBlank();
    }
}
