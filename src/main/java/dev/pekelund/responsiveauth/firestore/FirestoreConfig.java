package dev.pekelund.responsiveauth.firestore;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
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
@EnableConfigurationProperties(FirestoreProperties.class)
public class FirestoreConfig {

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
        Assert.isTrue(StringUtils.hasText(properties.getCredentials()),
            "Firestore credentials path must be provided when firestore.enabled is true");

        Resource resource = resourceLoader.getResource(properties.getCredentials());
        Assert.isTrue(resource.exists(),
            () -> "Firestore credentials resource not found at " + properties.getCredentials());

        try (InputStream inputStream = resource.getInputStream()) {
            FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
                .setCredentials(GoogleCredentials.fromStream(inputStream));
            if (StringUtils.hasText(properties.getProjectId())) {
                builder.setProjectId(properties.getProjectId());
            }
            return builder.build().getService();
        }
    }
}
