package dev.pekelund.pklnd.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
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

    @Test
    void fallsBackToEnvironmentWhenResourceMissing() {
        ApplicationContextRunner runner = contextRunner
            .withClassLoader(new HidingGitPropertiesClassLoader(getClass().getClassLoader()))
            .withPropertyValues(
                "GIT_BRANCH=feature/banner",
                "GIT_COMMIT=1234567890abcdef"
            );

        runner.run(context -> {
            assertThat(context).hasSingleBean(GitProperties.class);
            GitProperties gitProperties = context.getBean(GitProperties.class);
            assertThat(gitProperties.getBranch()).isEqualTo("feature/banner");
            assertThat(gitProperties.getShortCommitId()).isEqualTo("1234567");
        });
    }

    private static final class HidingGitPropertiesClassLoader extends ClassLoader {

        HidingGitPropertiesClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (name != null && name.endsWith("git.properties")) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (name != null && name.endsWith("git.properties")) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }
    }
}
