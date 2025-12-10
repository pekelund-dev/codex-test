package dev.pekelund.pklnd.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitMetadataTest {

    @Test
    void reportsAvailableWhenEitherValuePresent() {
        GitMetadata onlyCommit = new GitMetadata(null, "abc123", null);
        GitMetadata onlyBranch = new GitMetadata("main", " ", null);
        GitMetadata onlyVersion = new GitMetadata(null, null, "0.1.0-SNAPSHOT");

        assertThat(onlyCommit.available()).isTrue();
        assertThat(onlyBranch.available()).isTrue();
        assertThat(onlyVersion.available()).isTrue();
    }

    @Test
    void returnsPlaceholdersWhenValuesMissing() {
        GitMetadata empty = GitMetadata.empty();

        assertThat(empty.branchOrPlaceholder()).isEqualTo("okänd gren");
        assertThat(empty.commitOrPlaceholder()).isEqualTo("okänt commit-ID");
        assertThat(empty.versionOrPlaceholder()).isEqualTo("okänd version");
    }
}
