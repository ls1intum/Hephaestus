# syntax=docker/dockerfile:1.21@sha256:27f9262d43452075f3c410287a2c43f5ef1bf7ec2bb06e8c9eeb1b8d453087bc

FROM maven:3.9.12-eclipse-temurin-21@sha256:67dd489908ce4a87c1bea43c589b00c1121a64c1b0ca0f9f80902963655bdc73 AS build

WORKDIR /app

COPY pom.xml .

# Use BuildKit cache mount for Maven repository to speed up rebuilds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src

# Use BuildKit cache mount for Maven repository during build
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

FROM eclipse-temurin:21@sha256:2dbb970461f444a3230facb0a34ca9f2e555150689f0d943c252a3785c905487

WORKDIR /app

COPY --from=build /app/target/*.jar /app/server.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/server.jar"]