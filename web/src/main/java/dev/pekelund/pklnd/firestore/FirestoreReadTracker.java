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
    private final List<ReadOperation> readOperations = new ArrayList<>();

    public FirestoreReadTracker(FirestoreReadTotals totals) {
        this.totals = totals;
    }

    public void recordRead(String description) {
        recordRead(description, 1L);
    }

    public void recordRead(String description, long readUnits) {
        long units = Math.max(0L, readUnits);
        String label = StringUtils.hasText(description) ? description.trim() : "Unknown read";
        readOperations.add(new ReadOperation(label, units));
        totals.increment(units);
    }

    public long getReadCount() {
        return readOperations.stream().mapToLong(ReadOperation::readUnits).sum();
    }

    public List<ReadOperation> getReadOperations() {
        return Collections.unmodifiableList(readOperations);
    }

    public record ReadOperation(String description, long readUnits) { }
}
