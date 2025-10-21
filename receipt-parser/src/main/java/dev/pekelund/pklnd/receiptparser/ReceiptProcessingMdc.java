package dev.pekelund.pklnd.receiptparser;

import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * Utility for populating mapped diagnostic context (MDC) entries so log lines emitted during
 * receipt processing share the same identifiers (event id, bucket, object, owner, stage).
 */
final class ReceiptProcessingMdc {

    private static final String KEY_EVENT_ID = "receipt.eventId";
    private static final String KEY_BUCKET = "receipt.bucket";
    private static final String KEY_OBJECT = "receipt.object";
    private static final String KEY_OWNER_ID = "receipt.ownerId";
    private static final String KEY_OWNER_EMAIL = "receipt.ownerEmail";
    private static final String KEY_STAGE = "receipt.stage";

    private ReceiptProcessingMdc() {
        // Utility class
    }

    static Context open(String cloudEventId) {
        return new Context(cloudEventId);
    }

    static void attachEvent(StorageObjectEvent event) {
        if (event == null) {
            return;
        }
        putIfHasText(KEY_BUCKET, event.getBucket());
        putIfHasText(KEY_OBJECT, event.getName());
    }

    static void attachOwner(ReceiptOwner owner) {
        if (owner == null) {
            MDC.remove(KEY_OWNER_ID);
            MDC.remove(KEY_OWNER_EMAIL);
            return;
        }
        putIfHasText(KEY_OWNER_ID, owner.id());
        putIfHasText(KEY_OWNER_EMAIL, owner.email());
    }

    static void setStage(String stage) {
        if (!StringUtils.hasText(stage)) {
            MDC.remove(KEY_STAGE);
        } else {
            MDC.put(KEY_STAGE, stage);
        }
    }

    private static void putIfHasText(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }

    static final class Context implements AutoCloseable {

        private final Map<String, String> previous;

        private Context(String eventId) {
            this.previous = MDC.getCopyOfContextMap();
            putIfHasText(KEY_EVENT_ID, eventId);
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}

