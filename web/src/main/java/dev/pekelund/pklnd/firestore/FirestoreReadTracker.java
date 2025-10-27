package dev.pekelund.pklnd.firestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class FirestoreReadTracker {

    private final FirestoreReadTotals totals;
    private final List<String> readOperations = new ArrayList<>();

    public FirestoreReadTracker(FirestoreReadTotals totals) {
        this.totals = totals;
    }

    public void recordRead(String description) {
        String label = StringUtils.hasText(description) ? description.trim() : "Unknown read";
        readOperations.add(label);
        totals.increment();
    }

    public int getReadCount() {
        return readOperations.size();
    }

    public List<String> getReadOperations() {
        return Collections.unmodifiableList(readOperations);
    }
}
