package dev.pekelund.responsiveauth.firestore;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(FirestoreProperties.class)
public class FirestoreConfig {

    private static final Logger log = LoggerFactory.getLogger(FirestoreConfig.class);

    private final FirestoreProperties properties;
    private final ResourceLoader resourceLoader;

    public FirestoreConfig(FirestoreProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    @ConditionalOnProperty(value = "firestore.enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public Firestore firestore() throws IOException {
        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder();

        if (StringUtils.hasText(properties.getProjectId())) {
            builder.setProjectId(properties.getProjectId());
        }

        if (StringUtils.hasText(properties.getEmulatorHost())) {
            Assert.isTrue(StringUtils.hasText(properties.getProjectId()),
                "Firestore project id must be provided when using the emulator");

            builder.setProjectId(properties.getProjectId())
                .setHost(properties.getEmulatorHost())
                .setCredentials(NoCredentials.getInstance());
            return builder.build().getService();
        }

        GoogleCredentials credentials = null;

        if (StringUtils.hasText(properties.getCredentials())) {
            Resource resource = resourceLoader.getResource(properties.getCredentials());
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    credentials = GoogleCredentials.fromStream(inputStream);
                }
            } else {
                log.warn("Firestore credentials resource {} not found; falling back to application default credentials.",
                    properties.getCredentials());
            }
        }

        if (credentials == null) {
            credentials = GoogleCredentials.getApplicationDefault();
        }

        builder.setCredentials(credentials);
        return builder.build().getService();
    }
}
