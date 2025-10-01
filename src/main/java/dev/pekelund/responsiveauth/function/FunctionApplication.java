package dev.pekelund.responsiveauth.function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Cloud Function auto-configuration.
 */
@SpringBootApplication
public class FunctionApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(FunctionApplication.class, args);
    }
}
