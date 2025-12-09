package dev.pekelund.pklnd.web;

import java.io.IOException;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

@Configuration
class GitPropertiesConfiguration {

    @Bean
    @ConditionalOnMissingBean(GitProperties.class)
    @ConditionalOnResource(resources = "classpath:/git.properties")
    GitProperties gitProperties() throws IOException {
        ClassPathResource resource = new ClassPathResource("git.properties");
        Properties properties = PropertiesLoaderUtils.loadProperties(resource);
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
        return new GitProperties(normalized);
    }
}
