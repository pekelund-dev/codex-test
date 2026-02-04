# Firestore migrations

When new functionality requires updates to existing Firestore documents, add a migration instead of recreating data.
The web application runs registered migrations at startup and stores applied versions in the `schema_migrations`
collection.

## Adding a migration

1. Create a new class that implements `dev.pekelund.pklnd.firestore.FirestoreMigration`.
2. Choose a unique, increasing `version()` number and a short `description()`.
3. Implement `apply(Firestore firestore)` with the data updates you need.
4. Ensure the new class is annotated as a Spring component so it is discovered and run on startup.

Migrations are executed in version order. If a migration fails, startup will halt so the issue can be addressed before
continuing.

## Operational notes

- Run migrations during a maintenance window or low-traffic period when you expect significant data updates.
- The runtime service account must have permissions to read and write the affected Firestore documents.
