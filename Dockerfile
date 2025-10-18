# syntax=docker/dockerfile:1.7

FROM ghcr.io/graalvm/jdk:21 AS build

WORKDIR /workspace

# Install the GraalVM native-image toolchain
RUN gu install native-image

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
    && ./mvnw -Pinclude-web -pl web -am -Pnative -DskipTests -DskipNativeTests native:compile-no-fork \
    && mkdir -p /workspace/dist \
    && cp web/target/responsive-auth-web /workspace/dist/responsive-auth-web

FROM gcr.io/distroless/cc-debian12:nonroot AS runtime

WORKDIR /app

COPY --from=build --chown=nonroot:nonroot /workspace/dist/responsive-auth-web /app/app

EXPOSE 8080
ENTRYPOINT ["/app/app"]
