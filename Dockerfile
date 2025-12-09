# syntax=docker/dockerfile:1.4

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

ARG GIT_BRANCH=""
ARG GIT_COMMIT=""

COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY receipt-parser/pom.xml receipt-parser/pom.xml
COPY web/pom.xml web/pom.xml

RUN mvn -B -Pinclude-web -pl web -am -DskipTests dependency:go-offline

COPY core core
COPY web web

RUN if [ -n "${GIT_BRANCH}${GIT_COMMIT}" ]; then \
      mkdir -p web/src/main/resources; \
      SHORT_COMMIT="${GIT_COMMIT}"; \
      SHORT_COMMIT=${SHORT_COMMIT:0:7}; \
      { \
        if [ -n "${GIT_BRANCH}" ]; then echo "branch=${GIT_BRANCH}"; fi; \
        if [ -n "${GIT_COMMIT}" ]; then echo "commit.id=${GIT_COMMIT}"; echo "commit.id.abbrev=${SHORT_COMMIT}"; fi; \
      } > web/src/main/resources/git.properties; \
    fi

RUN mvn -B -Pinclude-web -pl web -am -DskipTests package \
    && JAR_PATH="$(find web/target -maxdepth 1 -type f -name '*-SNAPSHOT.jar' ! -name '*original*' | head -n 1)" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:21-jre

ENV JAVA_OPTS=""
WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
