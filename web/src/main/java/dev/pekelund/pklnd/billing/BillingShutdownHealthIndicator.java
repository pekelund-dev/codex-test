package dev.pekelund.pklnd.billing;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator that reports the application as DOWN when billing shutdown is triggered.
 * This causes Cloud Run health checks to fail, prompting the platform to scale the service
 * to zero instances and stop generating costs.
 */
@Component
public class BillingShutdownHealthIndicator implements HealthIndicator {
    
    private final BillingShutdownService shutdownService;
    
    public BillingShutdownHealthIndicator(BillingShutdownService shutdownService) {
        this.shutdownService = shutdownService;
    }
    
    @Override
    public Health health() {
        if (shutdownService.isShutdownActive()) {
            return Health.down()
                .withDetail("reason", shutdownService.getShutdownReason())
                .withDetail("shutdownTimestamp", shutdownService.getShutdownTimestamp())
                .withDetail("message", "Application shutdown due to billing alert. Scaling to zero to prevent further costs.")
                .build();
        }
        return Health.up().build();
    }
}
