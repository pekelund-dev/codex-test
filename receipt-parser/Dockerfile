# syntax=docker/dockerfile:1.4

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

ARG GIT_BRANCH=""
ARG GIT_COMMIT=""

# Copy POMs first for better layer caching
COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY receipt-parser/pom.xml receipt-parser/pom.xml

# Download dependencies in a separate layer for caching
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl receipt-parser -am -DskipTests dependency:go-offline

# Copy source code
COPY core core
COPY receipt-parser receipt-parser

# Generate git properties if provided
RUN if [ -n "${GIT_BRANCH}${GIT_COMMIT}" ]; then \
      mkdir -p receipt-parser/src/main/resources; \
      SHORT_COMMIT=$(echo "${GIT_COMMIT}" | cut -c1-7); \
      { \
        if [ -n "${GIT_BRANCH}" ]; then echo "git.branch=${GIT_BRANCH}"; fi; \
        if [ -n "${GIT_COMMIT}" ]; then echo "git.commit.id=${GIT_COMMIT}"; echo "git.commit.id.abbrev=${SHORT_COMMIT}"; fi; \
      } > receipt-parser/src/main/resources/git.properties; \
    fi

# Build the application with Maven cache mount
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl receipt-parser -am -DskipTests package \
    && JAR_PATH="$(find receipt-parser/target -maxdepth 1 -type f -name '*-SNAPSHOT.jar' ! -name '*original*' | head -n 1)" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:21-jre

ENV JAVA_OPTS=""
WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
