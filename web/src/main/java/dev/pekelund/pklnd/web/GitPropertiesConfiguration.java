package dev.pekelund.pklnd.web;

import java.io.IOException;
import java.util.Properties;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

@Configuration
class GitPropertiesConfiguration {

    private static final String BRANCH_ENVIRONMENT_VARIABLE = "GIT_BRANCH";
    private static final String COMMIT_ENVIRONMENT_VARIABLE = "GIT_COMMIT";

    @Bean
    @ConditionalOnMissingBean(GitProperties.class)
    GitProperties gitProperties(Environment environment) throws IOException {
        Properties properties = loadFromResource().orElseGet(Properties::new);
        if (properties.isEmpty()) {
            properties = loadFromEnvironment(environment);
        }

        return new GitProperties(properties);
    }

    private Optional<Properties> loadFromResource() throws IOException {
        ClassPathResource resource = new ClassPathResource("git.properties");
        if (!resource.exists()) {
            return Optional.empty();
        }
        Properties properties = PropertiesLoaderUtils.loadProperties(resource);
        return Optional.of(normalize(properties));
    }

    private Properties loadFromEnvironment(Environment environment) {
        Properties properties = new Properties();
        String branch = environment.getProperty(BRANCH_ENVIRONMENT_VARIABLE);
        String commit = environment.getProperty(COMMIT_ENVIRONMENT_VARIABLE);

        if (hasText(branch)) {
            properties.put("branch", branch);
        }
        if (hasText(commit)) {
            properties.put("commit.id", commit);
            properties.put("commit.id.abbrev", abbreviate(commit));
        }
        return properties;
    }

    private Properties normalize(Properties properties) {
        Properties normalized = new Properties();
        properties.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String name = key.toString();
            if (name.startsWith("git.")) {
                name = name.substring(4);
            }
            normalized.put(name, value);
        });
        return normalized;
    }

    private String abbreviate(String commitId) {
        if (!hasText(commitId) || commitId.length() <= 7) {
            return commitId;
        }
        return commitId.substring(0, 7);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
