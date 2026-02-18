# AI Contributor Guidelines

These instructions apply to the entire repository. Create additional `AGENTS.md` files in subdirectories if you need module-specific guidance that overrides the advice here.

## GCP project configuration

The GCP `project_id` and other deployment variables for this repository are stored in
`infra/terraform/infrastructure/terraform.tfvars` (copied from
`infra/terraform/infrastructure/terraform.tfvars.example`).

When you need the project ID in a command, read it from `terraform.tfvars` first:
```bash
PROJECT_ID=$(grep '^project_id' infra/terraform/infrastructure/terraform.tfvars | awk -F'"' '{print $2}')
```
If `terraform.tfvars` does not exist, fall back to: `gcloud config get-value project`.

## Development practices
- Prefer incremental Maven builds. When changing backend code, run the narrowest test scope that covers your changes, e.g. `./mvnw -pl web -am test` for web updates or `./mvnw -pl receipt-parser -am test` for receipt processor work. Use `./mvnw verify` only when you need a full reactor build.
- Keep configuration in sync with the documentation. If you change environment variables, startup scripts, or expected commands, update the relevant files in `docs/` or the `README` in the same change.
- When you modify behavior or interfaces, update accompanying documentation (READMEs, guides, diagrams) in the same PR so everything stays current.
- Update or extend automated tests alongside code changes to prevent regressions and demonstrate coverage for the new behavior.
- Avoid committing secrets. Reference local environment files in documentation instead of adding real credentials to the repository.
- Ensure all user-facing copy in the application appears in Swedish. When adding or updating UI text, keep keys or identifiers readable, but render the text shown to end users in Swedish.

## Architecture and documentation
- Keep high-level architecture descriptions in `docs/system-architecture-diagrams.md` and cross-link important updates from `README.md`.
- When you introduce or modify major components, update the diagrams/notes in `docs/` instead of overloading this file. Mention the change in the relevant module's README if one exists.
- Add a short summary in the PR description when architectural decisions change so reviewers know to consult the refreshed docs.

## Code style
- Follow the existing formatting conventions in each language. For Java and Kotlin sources, match the style produced by the default IDE formatters.
- Keep HTML and Thymeleaf templates readable—indent nested tags consistently and wrap lines longer than 120 characters when practical.
- Write code, comments, and documentation in English so the codebase stays consistent for international contributors. Only translate user-visible content into Swedish.

## Testing notes
- Prefer Maven’s wrapper scripts (`./mvnw` / `./mvnw.cmd`) so that builds run with the repository’s pinned Maven version.
- Record all tests you execute in the final report with the exact command used.
