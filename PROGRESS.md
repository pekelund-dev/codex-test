# Split FirestoreUserService into repository and service layers

**Labels:** code-quality, refactoring

**Description**
`FirestoreUserService` combines user registration, authentication, admin role
management, and an in-memory fallback store in 646 lines.

**Tasks**
- [ ] Extract `FirestoreUserRepository` — CRUD operations
- [ ] Extract `UserAuthenticationService` — authentication and role resolution
- [ ] Extract `AdminManagementService` — admin promotion/demotion
- [ ] Update callers and tests

**Acceptance criteria**
- Clean separation of persistence, auth, and admin concerns
- All existing tests pass

**References**
- docs/architecture-review.md § 3.2 — Issue 3
