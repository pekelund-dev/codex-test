# Add JaCoCo test coverage reporting

**Labels:** testing, ci/cd, quick-win

**Description**
Configure the Maven JaCoCo plugin across all three modules (core, web, receipt-parser) so that
test coverage is measured on every build and reported in CI.

**Tasks**
- [ ] Add `jacoco-maven-plugin` to the parent POM
- [ ] Configure report goals (`prepare-agent`, `report`)
- [ ] Add a CI step to publish coverage summaries as PR comments or artifacts
- [ ] Set a baseline coverage threshold (e.g. fail build below 40 %)

**Acceptance criteria**
- `./mvnw verify` generates `target/site/jacoco/index.html` for each module
- PR validation workflow uploads or comments a coverage summary

**References**
- docs/architecture-review.md § 7 — Testing Review
