# syntax=docker/dockerfile:1.23@sha256:2780b5c3bab67f1f76c781860de469442999ed1a0d7992a5efdf2cffc0e3d769

FROM maven:3.9.14-eclipse-temurin-21@sha256:98819eb3745bd2007c3f1a19b59085c1fa3929aecb7dbfa431dfcf5a4f18ce3c AS build

WORKDIR /app

COPY pom.xml .

# Use BuildKit cache mount for Maven repository to speed up rebuilds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src

# Use BuildKit cache mount for Maven repository during build
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

FROM eclipse-temurin:21@sha256:e58e492628c1428ceb838afc1a1b8762673d5eaa09296f560c363daea0fdcf3b

# git CLI required by PullRequestReviewHandler.runGit() for diff computation
RUN apt-get update -qq && apt-get install -y --no-install-recommends git ca-certificates && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/*.jar /app/server.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring
RUN mkdir -p /var/log/hephaestus/access && chown -R spring:spring /var/log/hephaestus

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/server.jar"]
