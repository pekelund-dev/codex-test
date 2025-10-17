# syntax=docker/dockerfile:1.7

FROM ghcr.io/graalvm/jdk:21 AS build

WORKDIR /workspace

# Pre-fetch dependencies for faster incremental builds
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY core/pom.xml core/pom.xml
COPY function/pom.xml function/pom.xml
COPY web/pom.xml web/pom.xml

RUN ./mvnw -Pinclude-web -pl web -am -DskipTests dependency:go-offline

# Build the full project
COPY core core
COPY function function
COPY web web

RUN ./mvnw -Pinclude-web -pl web -am -DskipTests package \
    && mkdir -p /workspace/dist \
    && JAR_PATH=$(find web/target -maxdepth 1 -type f -name '*SNAPSHOT.jar' ! -name '*original*' | head -n 1) \
    && cp "$JAR_PATH" /workspace/dist/app.jar

FROM gcr.io/distroless/java21-debian12:nonroot AS runtime

ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=75"
WORKDIR /app

COPY --from=build /workspace/dist/app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
