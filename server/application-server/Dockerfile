# syntax=docker/dockerfile:1.23@sha256:2780b5c3bab67f1f76c781860de469442999ed1a0d7992a5efdf2cffc0e3d769

FROM maven:3.9.14-eclipse-temurin-21@sha256:481aad0b7042ad7a924ebd49ea5b307a049bd33d758f8613d68dcbd2904f3c8e AS build

WORKDIR /app

COPY pom.xml .

# Use BuildKit cache mount for Maven repository to speed up rebuilds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src

# Use BuildKit cache mount for Maven repository during build
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

FROM eclipse-temurin:21@sha256:a92ba725ca645acfddb8df54c6280ca770b8312d03debd6a71e7477b83df5ef2

WORKDIR /app

COPY --from=build /app/target/*.jar /app/server.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/server.jar"]