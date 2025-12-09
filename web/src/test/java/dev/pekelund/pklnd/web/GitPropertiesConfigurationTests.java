package dev.pekelund.pklnd.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GitPropertiesConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(GitPropertiesConfiguration.class);

    @Test
    void exposesGitPropertiesWhenResourcePresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GitProperties.class);
            GitProperties gitProperties = context.getBean(GitProperties.class);
            assertThat(gitProperties.getBranch()).isEqualTo("main");
            assertThat(gitProperties.getShortCommitId()).isEqualTo("abc123");
        });
    }
}
