package dev.pekelund.responsiveauth.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

    private final GcsProperties properties;
    private final ResourceLoader resourceLoader;

    public GcsConfig(GcsProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    @ConditionalOnProperty(value = "gcs.enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public Storage storage() throws IOException {
        StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();
        GoogleCredentials credentials;

        if (StringUtils.hasText(properties.getCredentials())) {
            Resource resource = resourceLoader.getResource(properties.getCredentials());
            Assert.isTrue(resource.exists(),
                () -> "Google Cloud credentials resource not found at " + properties.getCredentials());
            try (InputStream inputStream = resource.getInputStream()) {
                credentials = GoogleCredentials.fromStream(inputStream);
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }

        optionsBuilder.setCredentials(credentials);
        if (StringUtils.hasText(properties.getProjectId())) {
            optionsBuilder.setProjectId(properties.getProjectId());
        }

        return optionsBuilder.build().getService();
    }
}

