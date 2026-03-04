package dev.pekelund.pklnd.web;

import dev.pekelund.pklnd.storage.ReceiptOwner;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manages the demo-mode session for anonymous users.
 * A demo session is identified by a UUID stored as an HTTP-session attribute.
 * The UUID is used as the {@link ReceiptOwner#id()} so that uploaded receipts
 * are scoped to the session.
 */
@Service
public class DemoSessionService {

    public static final String DEMO_USER_ID_ATTRIBUTE = "demoUserId";
    public static final int MAX_DEMO_UPLOADS = 5;

    /**
     * Returns the demo {@link ReceiptOwner} for the given session, creating one if absent.
     */
    public ReceiptOwner getOrCreateDemoOwner(HttpSession session) {
        String demoId = (String) session.getAttribute(DEMO_USER_ID_ATTRIBUTE);
        if (demoId == null) {
            demoId = "demo-" + UUID.randomUUID();
            session.setAttribute(DEMO_USER_ID_ATTRIBUTE, demoId);
        }
        return new ReceiptOwner(demoId, "Demo", null);
    }

    /**
     * Returns the demo {@link ReceiptOwner} if a demo session is active, otherwise {@code null}.
     */
    public ReceiptOwner getDemoOwner(HttpSession session) {
        String demoId = (String) session.getAttribute(DEMO_USER_ID_ATTRIBUTE);
        if (demoId == null) {
            return null;
        }
        return new ReceiptOwner(demoId, "Demo", null);
    }

    /**
     * Returns {@code true} when the given session contains an active demo user ID.
     */
    public boolean isDemoSession(HttpSession session) {
        return session.getAttribute(DEMO_USER_ID_ATTRIBUTE) != null;
    }
}
