package dev.pekelund.pklnd.billing;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service that manages the application shutdown state when billing alerts are triggered.
 * When the budget threshold is exceeded, this service enters a shutdown state that
 * causes health checks to fail, prompting Cloud Run to scale the service to zero.
 */
@Service
public class BillingShutdownService {
    private static final Logger logger = LoggerFactory.getLogger(BillingShutdownService.class);
    
    private final AtomicBoolean shutdownTriggered = new AtomicBoolean(false);
    private volatile String shutdownReason;
    private volatile long shutdownTimestamp;
    
    /**
     * Triggers the billing shutdown, causing the application to report unhealthy
     * and scale to zero to stop generating costs.
     * 
     * @param reason the reason for the shutdown (e.g., "Budget exceeded: 100%")
     */
    public void triggerShutdown(String reason) {
        if (shutdownTriggered.compareAndSet(false, true)) {
            this.shutdownReason = reason;
            this.shutdownTimestamp = System.currentTimeMillis();
            logger.error("BILLING ALERT SHUTDOWN TRIGGERED: {}", reason);
            logger.error("Application will report unhealthy and scale to zero to stop generating costs");
            logger.error("To re-enable, restart the Cloud Run service or redeploy");
        }
    }
    
    /**
     * Checks if the billing shutdown has been triggered.
     * 
     * @return true if shutdown is active, false otherwise
     */
    public boolean isShutdownActive() {
        return shutdownTriggered.get();
    }
    
    /**
     * Gets the reason for the shutdown.
     * 
     * @return the shutdown reason, or null if not shutdown
     */
    public String getShutdownReason() {
        return shutdownReason;
    }
    
    /**
     * Gets the timestamp when shutdown was triggered.
     * 
     * @return the shutdown timestamp in milliseconds since epoch
     */
    public long getShutdownTimestamp() {
        return shutdownTimestamp;
    }
}
