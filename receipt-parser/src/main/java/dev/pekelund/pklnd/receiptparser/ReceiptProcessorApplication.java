package dev.pekelund.pklnd.receiptparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the receipt processing service.
 */
@SpringBootApplication
public class ReceiptProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReceiptProcessorApplication.class, args);
    }
}
