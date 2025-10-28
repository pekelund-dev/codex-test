package dev.pekelund.pklnd.config;

import dev.pekelund.pklnd.firestore.FirestoreReadLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FirestoreMonitoringConfig implements WebMvcConfigurer {

    private final FirestoreReadLoggingInterceptor firestoreReadLoggingInterceptor;

    public FirestoreMonitoringConfig(FirestoreReadLoggingInterceptor firestoreReadLoggingInterceptor) {
        this.firestoreReadLoggingInterceptor = firestoreReadLoggingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(firestoreReadLoggingInterceptor).addPathPatterns("/**");
    }
}
