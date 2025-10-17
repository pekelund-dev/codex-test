# syntax=docker/dockerfile:1.7

FROM ghcr.io/graalvm/jdk:21 AS build

WORKDIR /workspace

# Copy Maven wrapper and module descriptors for dependency resolution
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY core/pom.xml core/pom.xml
COPY function/pom.xml function/pom.xml

RUN ./mvnw -pl function -am -DskipTests dependency:go-offline

# Build the function module and package a runnable fat JAR
COPY core core
COPY function function

RUN ./mvnw -pl function -am -DskipTests -Dspring-boot.repackage.skip=false package \
    && mkdir -p /workspace/dist \
    && JAR_PATH=$(find target -maxdepth 1 -type f -name '*SNAPSHOT.jar' ! -name '*original*' | head -n 1) \
    && cp "$JAR_PATH" /workspace/dist/function.jar

FROM ghcr.io/graalvm/jdk:21 AS runtime

ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=75" \
    FUNCTION_TARGET="dev.pekelund.pklnd.function.ReceiptProcessingFunction"

WORKDIR /app

COPY --from=build /workspace/dist/function.jar /app/function.jar

ENTRYPOINT ["java","-jar","/app/function.jar"]
