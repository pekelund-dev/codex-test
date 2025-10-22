# syntax=docker/dockerfile:1.4

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY receipt-parser/pom.xml receipt-parser/pom.xml

RUN mvn -B -pl receipt-parser -am -DskipTests dependency:go-offline

COPY core core
COPY receipt-parser receipt-parser

RUN mvn -B -pl receipt-parser -am -DskipTests package \
    && JAR_PATH="$(find receipt-parser/target -maxdepth 1 -type f -name '*-SNAPSHOT.jar' ! -name '*original*' | head -n 1)" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:21-jre

ENV JAVA_OPTS=""
WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
