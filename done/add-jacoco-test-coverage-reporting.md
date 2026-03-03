# Add JaCoCo test coverage reporting

**Labels:** testing, ci/cd, quick-win

**Description**
Configure the Maven JaCoCo plugin across all three modules (core, web, receipt-parser) so that
test coverage is measured on every build and reported in CI.

**Tasks**
- [x] Add `jacoco-maven-plugin` to the parent POM
- [x] Configure report goals (`prepare-agent`, `report`)
- [x] Add a CI step to publish coverage summaries as PR comments or artifacts
- [x] Set a baseline coverage threshold (35% instruction coverage — web: 36%, receipt-parser: 46%, core: 56%)

**Acceptance criteria**
- [x] `./mvnw verify` generates `target/site/jacoco/index.html` for each module
- [x] PR validation workflow uploads coverage reports as artifacts

**References**
- docs/architecture-review.md § 7 — Testing Review
