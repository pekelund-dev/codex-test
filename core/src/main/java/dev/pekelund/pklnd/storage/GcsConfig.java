package dev.pekelund.pklnd.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

    private static final Logger log = LoggerFactory.getLogger(GcsConfig.class);

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
        GoogleCredentials credentials = null;

        if (StringUtils.hasText(properties.getCredentials())) {
            Resource resource = resourceLoader.getResource(properties.getCredentials());
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    credentials = GoogleCredentials.fromStream(inputStream);
                }
            } else {
                log.warn("GCS credentials resource {} not found; falling back to application default credentials.",
                    properties.getCredentials());
            }
        }

        if (credentials == null) {
            credentials = GoogleCredentials.getApplicationDefault();
        }

        optionsBuilder.setCredentials(credentials);
        if (StringUtils.hasText(properties.getProjectId())) {
            optionsBuilder.setProjectId(properties.getProjectId());
        }

        return optionsBuilder.build().getService();
    }
}

