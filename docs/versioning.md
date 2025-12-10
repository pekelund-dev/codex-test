# Versioning Strategy

This document describes the versioning approach used in the pklnd application.

## Overview

The application uses a multi-layered versioning strategy that combines:
1. **Semantic versioning** for Maven artifacts
2. **Git-based versioning** for runtime metadata
3. **Container image tagging** for deployments

## Maven Semantic Versioning

The project follows [Semantic Versioning 2.0.0](https://semver.org/) for Maven artifacts:

- **MAJOR** version: Incompatible API changes
- **MINOR** version: Backwards-compatible functionality additions
- **PATCH** version: Backwards-compatible bug fixes
- **SNAPSHOT** suffix: Development/unreleased versions

Current version is defined in the parent `pom.xml`:

```xml
<version>0.1.0-SNAPSHOT</version>
```

All child modules (core, web, receipt-parser) inherit this version from the parent POM.

### Releasing a New Version

To prepare a release, update the version in the parent `pom.xml`. For example, to release version 0.1.0:

1. Remove the `-SNAPSHOT` suffix:
   ```xml
   <version>0.1.0</version>
   ```

2. Build and tag the release:
   ```bash
   git tag -a v0.1.0 -m "Release version 0.1.0"
   git push origin v0.1.0
   ```

3. After release, increment to the next development version:
   ```xml
   <version>0.2.0-SNAPSHOT</version>
   ```

**Note**: The version is automatically extracted from `pom.xml` during Docker builds, so no manual synchronization with Dockerfiles is needed. The build process reads the version from the parent POM and embeds it in the `git.properties` file.

## Git-Based Runtime Versioning

The application uses the [git-commit-id-plugin](https://github.com/git-commit-id/git-commit-id-maven-plugin) to embed Git metadata at build time. This metadata is accessible at runtime through Spring Boot's `GitProperties` and displayed in the application UI.

### Generated Properties

The plugin generates a `git.properties` file in the application JAR containing:

- `git.branch` - The branch name from which the build was made
- `git.commit.id.abbrev` - The abbreviated commit SHA (7 characters)
- `git.build.version` - The Maven version at build time
- `git.build.time` - The timestamp when the build was executed

### Configuration

Both the `web` and `receipt-parser` modules are configured with the plugin in their `pom.xml`:

```xml
<plugin>
    <groupId>pl.project13.maven</groupId>
    <artifactId>git-commit-id-plugin</artifactId>
    <version>4.9.10</version>
    <executions>
        <execution>
            <id>get-the-git-infos</id>
            <goals>
                <goal>revision</goal>
            </goals>
            <phase>initialize</phase>
        </execution>
    </executions>
    <configuration>
        <failOnNoGitDirectory>false</failOnNoGitDirectory>
        <generateGitPropertiesFile>true</generateGitPropertiesFile>
        <generateGitPropertiesFilename>${project.build.outputDirectory}/git.properties</generateGitPropertiesFilename>
        <includeOnlyProperties>
            <includeOnlyProperty>^git.branch$</includeOnlyProperty>
            <includeOnlyProperty>^git.commit.id$</includeOnlyProperty>
            <includeOnlyProperty>^git.commit.id.abbrev$</includeOnlyProperty>
            <includeOnlyProperty>^git.build.version$</includeOnlyProperty>
            <includeOnlyProperty>^git.build.time$</includeOnlyProperty>
        </includeOnlyProperties>
        <commitIdGenerationMode>full</commitIdGenerationMode>
    </configuration>
</plugin>
```

### UI Display

The web application displays version information in the navigation bar (see `web/src/main/resources/templates/layout.html`):

```html
<div class="bg-danger text-white">
    <div class="container small py-1 text-center text-md-start">
        <span class="fw-semibold"
              th:text="${#messages.msg('nav.git.info', gitMetadata.branchOrPlaceholder(), gitMetadata.commitOrPlaceholder(), gitMetadata.versionOrPlaceholder())}">
            Version: 0.1.0-SNAPSHOT · Gren: main · commit: abc123
        </span>
    </div>
</div>
```

This provides immediate visibility into which version of the code is running, including the semantic version, branch name, and commit hash.

## Container Image Versioning

Docker images are tagged with both a timestamp and the `latest` tag during deployment.

### Build-Time Git Metadata Injection

Both Dockerfiles (`Dockerfile` for web, `receipt-parser/Dockerfile` for receipt-parser) accept git metadata as build arguments and generate the `git.properties` file at Docker build time. This is necessary because Cloud Build operates on a source archive without the full git repository history, so the Maven git-commit-id plugin cannot access git metadata during the Cloud Build process.

The Dockerfiles include logic to generate git.properties from build arguments and automatically extract the version from the parent POM:

```dockerfile
ARG GIT_BRANCH=""
ARG GIT_COMMIT=""

# Generate git properties if provided (for Cloud Build where git repo isn't available)
RUN if [ -n "${GIT_BRANCH}${GIT_COMMIT}" ]; then \
      mkdir -p receipt-parser/src/main/resources; \
      SHORT_COMMIT=$(echo "${GIT_COMMIT}" | cut -c1-7); \
      BUILD_TIME=$(date -u +"%Y-%m-%dT%H:%M:%S%z"); \
      PROJECT_VERSION=$(grep -oP '<version>\K[^<]+' pom.xml | head -1); \
      { \
        echo "#Generated by Dockerfile build"; \
        if [ -n "${GIT_BRANCH}" ]; then echo "git.branch=${GIT_BRANCH}"; fi; \
        if [ -n "${GIT_COMMIT}" ]; then \
          echo "git.commit.id=${GIT_COMMIT}"; \
          echo "git.commit.id.abbrev=${SHORT_COMMIT}"; \
        fi; \
        echo "git.build.time=${BUILD_TIME}"; \
        echo "git.build.version=${PROJECT_VERSION}"; \
      } > receipt-parser/src/main/resources/git.properties; \
    fi

# Build with git-commit-id-plugin disabled to use Docker-generated git.properties
RUN mvn -B -pl receipt-parser -am -DskipTests -Dgit-commit-id-plugin.skip=true package
```

**Note**: The path in this example is `receipt-parser/src/main/resources` for the receipt-parser module; the web module uses `web/src/main/resources` instead. When building locally with Maven (outside Docker), the git-commit-id plugin generates this file automatically with the same properties. During Docker builds, the plugin is explicitly disabled via `-Dgit-commit-id-plugin.skip=true` to ensure the Docker-generated git.properties file is used instead of having the plugin attempt to generate one.

### Cloud Build Configuration

The deployment scripts (`scripts/terraform/deploy_services.sh`) automatically extract Git information and pass it to Cloud Build:

```bash
build_branch=${BRANCH_NAME:-$(git -C "${REPO_ROOT}" rev-parse --abbrev-ref HEAD 2>/dev/null || true)}
build_commit=${COMMIT_SHA:-$(git -C "${REPO_ROOT}" rev-parse HEAD 2>/dev/null || true)}

gcloud builds submit "${WEB_BUILD_CONTEXT}" \
  --config "${REPO_ROOT}/cloudbuild.yaml" \
  --substitutions "_IMAGE_BASE=${web_image_base},_IMAGE_TAG=${timestamp},_DOCKERFILE=${WEB_DOCKERFILE},_GIT_BRANCH=${build_branch},_GIT_COMMIT=${build_commit}" \
  --project "${PROJECT_ID}"
```

The Cloud Build YAML files (`cloudbuild.yaml` and `receipt-parser/cloudbuild.yaml`) define default substitutions:

```yaml
substitutions:
  _IMAGE_BASE: 'us-east1-docker.pkg.dev/PROJECT_ID/web/pklnd-web'
  _IMAGE_TAG: 'latest'
  _DOCKERFILE: 'Dockerfile'
  _GIT_BRANCH: 'main'
  _GIT_COMMIT: 'unknown'
```

### Image Tags

Each deployment creates images with two tags:

1. **Timestamp tag**: `YYYYMMDD-HHMMSS` - Unique identifier for each build
2. **Latest tag**: `latest` - Always points to the most recent build

Example:
```
us-east1-docker.pkg.dev/my-project/web/pklnd-web:20231210-123045
us-east1-docker.pkg.dev/my-project/web/pklnd-web:latest
```

This approach allows:
- Rollback to specific deployments using timestamp tags
- Easy updates using the `latest` tag
- Image retention policies based on timestamp patterns

### Artifact Cleanup

The deployment script (`scripts/terraform/deploy_services.sh`) automatically cleans up old container images to reduce storage costs:

- Keeps the last 3 timestamped images for each service (web and receipt-parser)
- Preserves the `latest` tag and buildcache
- Deletes older images automatically after each deployment
- Cleans up Cloud Build source cache

This automatic cleanup ensures that artifact storage costs remain manageable while retaining recent deployment history for rollback purposes. The retention count (3 images) can be adjusted by modifying the cleanup logic in the deployment script.

For manual cleanup or custom retention policies, use:
```bash
PROJECT_ID=your-project KEEP_IMAGES=5 ./scripts/terraform/cleanup_artifacts.sh
```

## Benefits

This versioning strategy provides:

1. **Traceability**: Every running instance can be traced back to exact source code via commit SHA
2. **Debugging**: Developers can quickly identify which version is deployed in each environment
3. **Rollback**: Timestamp-based image tags enable precise rollback to any previous deployment
4. **Auditability**: Build metadata is preserved in both Maven artifacts and container images
5. **User Visibility**: End users can see version information in the UI for bug reporting

## Automation Opportunities

The current implementation is semi-automated. Future improvements could include:

- GitHub Actions workflow to automatically bump versions on merge to main
- Release automation using semantic-release or similar tools
- Automated changelog generation from commit messages
- Version badge generation for documentation
- API endpoint exposing version information for monitoring tools
