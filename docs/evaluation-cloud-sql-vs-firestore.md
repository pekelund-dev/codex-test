# Evaluation: Cloud SQL vs Firestore for Receipt Data

**Date:** 2026-02-27  
**Status:** Recommendation — Stay with Firestore

---

## Current State

Receipts and items are stored in Firestore top-level collections (`parsed-receipts`, `receipt-items`).
Common queries filter by `owner.uid`, `receiptDate`, and `storeName` with composite indexes.

## Cloud SQL (PostgreSQL) Analysis

### Advantages
- Richer query language (GROUP BY, window functions, full-text search)
- Simpler date-range + multi-column filter queries without composite index management
- Familiar relational schema

### Disadvantages
- **Cost**: Cloud SQL minimum ~$50/month (db-f1-micro); Firestore is pay-per-read (near-zero for low-volume personal use)
- **Operational complexity**: instance management, backups, connection pooling
- **Migration risk**: existing Firestore data requires a one-time migration script
- **No horizontal scale** without Cloud Spanner (different product)

### Estimated Queries & Index Complexity

Current filter combinations:
- `owner.uid == X` (1 index)
- `owner.uid == X AND receiptDate >= Y AND receiptDate <= Z` (1 composite index)
- `owner.uid == X AND storeName == Y` (1 composite index)

These are manageable with the existing Firestore composite index strategy.

## Recommendation

**Stay with Firestore** for the following reasons:
1. Cost: Firestore at this scale costs < $1/month; Cloud SQL would cost ≥ $50/month.
2. The current composite index approach handles all production query patterns.
3. Migration risk outweighs the benefits at this stage.

**Revisit** if the receipt collection exceeds 100,000 documents or complex analytics queries
(GROUP BY store, time-series aggregation) become a bottleneck.
