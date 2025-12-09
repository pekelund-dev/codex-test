package dev.pekelund.pklnd.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitMetadataTest {

    @Test
    void reportsAvailableWhenEitherValuePresent() {
        GitMetadata onlyCommit = new GitMetadata(null, "abc123");
        GitMetadata onlyBranch = new GitMetadata("main", " ");

        assertThat(onlyCommit.available()).isTrue();
        assertThat(onlyBranch.available()).isTrue();
    }

    @Test
    void returnsPlaceholdersWhenValuesMissing() {
        GitMetadata empty = GitMetadata.empty();

        assertThat(empty.branchOrPlaceholder()).isEqualTo("okänd gren");
        assertThat(empty.commitOrPlaceholder()).isEqualTo("okänt commit-ID");
    }
}
