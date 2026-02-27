# Migrate to Firestore subcollections for receipt items

**Status:** Deferred — requires careful data migration planning

**Blocked:** Migration script requires write access to production Firestore.
The subcollection design (receipts/{id}/items) is documented but the migration
requires a one-time script run against production data with a maintenance window.
This is deferred until a planned maintenance window is available.
