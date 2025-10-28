# Firestore Read Optimization

## Overview
This document describes the Firestore data structure optimizations implemented to reduce the number of reads and stay within the daily free tier limits.

## Problem Statement
The read count was quickly approaching the daily free limit in Firestore (50,000 reads/day in the free tier). The main issues were:

1. **Inefficient deletion queries**: `deleteReceiptsForOwner()` was reading ALL receipts then filtering
2. **Potential redundant stats queries**: Risk of loading item statistics when they're already embedded in receipts

## Optimizations Implemented

### 1. Optimized Receipt Deletion Query (IMPLEMENTED)

**File**: `web/src/main/java/dev/pekelund/pklnd/firestore/ReceiptExtractionService.java`

**Before**:
```java
// Read ALL documents, then filter by owner in-memory
Iterable<DocumentReference> documents = firestore.get()
    .collection(properties.getReceiptsCollection())
    .listDocuments();
for (DocumentReference document : documents) {
    DocumentSnapshot snapshot = document.get().get(); // N reads
    if (!belongsToCurrentOwner(receipt.owner(), owner)) {
        continue; // Skip non-matching receipts
    }
    // Delete matching receipt
}
```

**After**:
```java
// Use filtered query to read only matching documents
QuerySnapshot snapshot = firestore.get()
    .collection(properties.getReceiptsCollection())
    .whereEqualTo("owner.id", owner.id())  // Filter server-side
    .get()
    .get(); // Only M reads (where M = owner's receipts)

for (DocumentSnapshot document : snapshot.getDocuments()) {
    // Delete matching receipt
}
```

**Impact**: Reduces reads from N (total receipts) to M (owner's receipts only)
- Example: If there are 1000 total receipts and a user owns 10, this reduces reads from 1000 to 10 (99% reduction)

### 2. Item History Embedding (ALREADY IMPLEMENTED)

**Files**: 
- `receipt-parser/src/main/java/dev/pekelund/pklnd/receiptparser/ReceiptExtractionRepository.java`
- `web/src/main/java/dev/pekelund/pklnd/web/ReceiptController.java`

The receipt parser already embeds item occurrence counts (`itemHistory`) directly in each receipt document during write operations. The web application already uses this embedded data first before falling back to querying the stats collection.

```java
// In ReceiptController.resolveItemOccurrences()
ParsedReceipt.ReceiptItemHistory history = receipt.itemHistory();
if (history != null && history.containsAll(normalizedEans, useGlobal)) {
    // Use embedded data - no additional reads needed
    return extractCountsFromHistory(history, normalizedEans);
}
// Only fall back to stats collection if data is missing
return receiptExtractionService.get().loadItemOccurrences(...);
```

**Impact**: Eliminates stats collection reads for receipt detail views when itemHistory is present
- Saves up to 10 reads per receipt view (depending on number of unique items)

### 3. Composite Indexes (RECOMMENDED)

**File**: `firestore.indexes.json`

Created composite index definitions to ensure efficient query performance:

1. **Receipt queries by owner**: `owner.id` + `updatedAt` (for sorted owner receipt lists)
2. **Receipt item lookups**: `receiptId` (for deletion and reference queries)
3. **Item reference queries**: `normalizedEan` + `ownerId` (for item history lookup)

To deploy these indexes:
```bash
firebase deploy --only firestore:indexes
```

## Data Structure

### Collections

1. **receiptExtractions** - Main receipt documents
   - Contains: bucket, objectName, owner, status, data (structured), items, itemHistory
   - itemHistory embedded: `{ owner: {ean: count}, global: {ean: count} }`

2. **receiptItems** - Denormalized item references (for efficient item-to-receipt lookup)
   - Contains: receiptId, normalizedEan, ownerId, itemData, receiptDate, receiptStoreName
   - One document per item occurrence

3. **receiptItemStats** - Aggregated statistics
   - Document ID: `{ownerId}#{normalizedEan}`
   - Contains: count, lastReceiptId, lastReceiptDate, lastStoreName, updatedAt
   - Used as fallback when itemHistory is incomplete

## Read Patterns

### Efficient Patterns ✅
- **Receipt list by owner**: `whereEqualTo("owner.id")` - reads only owner's receipts
- **Single receipt by ID**: Direct document read - 1 read
- **Item occurrences**: Uses embedded itemHistory first - 0 additional reads
- **Item references**: `whereEqualTo("normalizedEan")` with optional owner filter

### Patterns to Avoid ❌
- **Loading all receipts**: Should filter by owner unless admin viewing all
- **Individual document reads in loops**: Use batch queries with `whereIn` instead
- **Querying stats when itemHistory is available**: Check embedded data first

## Monitoring Read Count

Use the `FirestoreReadTracker` component to monitor reads per request:
- Enabled in request scope
- Records description and count for each read operation
- Totals available via `FirestoreReadTotals` singleton

View read statistics in the application dashboard.

## Future Optimization Opportunities

1. **Receipt Overview Pagination**: The overview page loads all receipts to build period summaries. Consider:
   - Adding summary fields to receipts (itemCount, totalValue)
   - Creating a separate summaries collection
   - Implementing server-side aggregation

2. **Selective Field Loading**: Firestore doesn't support field projections, but we could:
   - Create lightweight summary documents for list views
   - Store minimal data in a separate collection for dashboards

3. **Caching**: Implement application-level caching for:
   - Recent receipt lists (short TTL)
   - Item statistics (longer TTL with invalidation on updates)

4. **Batch Operations**: Ensure all multi-document operations use batched queries
   - Already implemented for item stats loading (max 10 per query)
   - Deletion uses filtered queries

## Testing

To verify read count reductions:

1. Enable Firestore read tracking in tests
2. Mock Firestore with read counting
3. Compare before/after read counts for common operations

Example test scenario:
```
Scenario: Delete receipts for user with 5 receipts out of 100 total
Before: 100 reads (all receipts) + 5 item queries + 5 deletions
After: 5 reads (filtered query) + 5 item queries + 5 deletions
Savings: 95 reads (95% reduction)
```

## References

- [Firestore Best Practices](https://firebase.google.com/docs/firestore/best-practices)
- [Firestore Query Limitations](https://firebase.google.com/docs/firestore/query-data/queries)
- [Firestore Pricing](https://firebase.google.com/pricing)
