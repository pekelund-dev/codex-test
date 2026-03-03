# Split FirestoreUserService into repository and service layers

**Labels:** code-quality, refactoring

**Description**
`FirestoreUserService` combines user registration, authentication, admin role
management, and an in-memory fallback store in 646 lines.

**Tasks**
- [x] Extract `FirestoreUserRepository` — CRUD operations (323 lines)
- [x] Extract `UserAuthenticationService` — authentication and role resolution (141 lines)
- [x] Extract `AdminManagementService` — admin promotion/demotion (161 lines)
- [x] `FirestoreUserService` reduced to 167-line facade; all callers unchanged

**Acceptance criteria**
- [x] Clean separation of persistence, auth, and admin concerns
- [x] All existing tests pass (74/74)

**References**
- docs/architecture-review.md § 3.2 — Issue 3
