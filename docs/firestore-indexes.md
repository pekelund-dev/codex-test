# Firestore index requirements

The web application relies on Firestore query patterns that require composite indexes. This guide lists the indexes
needed to keep the receipts experience responsive.

## Parsed receipts collection

Create these composite indexes on the parsed receipts collection (configured via `firestore.receiptsCollection`):

1. **Receipts by owner and receipt date**
   - Collection: parsed receipts
   - Fields:
     - `ownerId` **Ascending**
     - `general.receiptDate` **Descending**

2. **Receipts by owner and store name**
   - Collection: parsed receipts
   - Fields:
     - `ownerId` **Ascending**
     - `general.storeName` **Ascending**

3. **Receipts by owner and created timestamp**
   - Collection: parsed receipts
   - Fields:
     - `ownerId` **Ascending**
     - `createdAt` **Descending**

If Firestore reports a missing index error in the logs, follow the link in the error message to create the suggested
index in the Firebase/Google Cloud console. Keep this document updated whenever new query patterns are introduced.
