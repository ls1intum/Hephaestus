# syntax=docker/dockerfile:1.22@sha256:4a43a54dd1fedceb30ba47e76cfcf2b47304f4161c0caeac2db1c61804ea3c91

FROM maven:3.9.14-eclipse-temurin-21@sha256:fe6cfb95c7ec52702f41b3071cae4cf69b8b8cc224104cfe0cad65b36e9107a5 AS build

WORKDIR /app

COPY pom.xml .

# Use BuildKit cache mount for Maven repository to speed up rebuilds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src

# Use BuildKit cache mount for Maven repository during build
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests

FROM eclipse-temurin:21@sha256:3b0a98dfbdf1067c20a7854cec159551777d2ee1381bc76cd4bd0719f543b148

WORKDIR /app

COPY --from=build /app/target/*.jar /app/server.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/server.jar"]