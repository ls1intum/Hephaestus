FROM maven:3.9.8-eclipse-temurin-21 AS build

ARG GIT_SHA=unknown
ARG IMAGE_TAG=unknown
ARG BUILD_DATE=unknown

ENV GIT_SHA=${GIT_SHA}
ENV IMAGE_TAG=${IMAGE_TAG}
ENV BUILD_DATE=${BUILD_DATE}

WORKDIR /app

COPY pom.xml .

# This step is to cache dependencies, avoiding re-downloading them every time
RUN mvn -B -U dependency:go-offline

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21

ARG GIT_SHA=unknown
ARG IMAGE_TAG=unknown
ARG BUILD_DATE=unknown

ENV GIT_SHA=${GIT_SHA}
ENV IMAGE_TAG=${IMAGE_TAG}
ENV BUILD_DATE=${BUILD_DATE}

LABEL org.opencontainers.image.revision="${GIT_SHA}"
LABEL org.opencontainers.image.version="${IMAGE_TAG}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"

WORKDIR /app

COPY --from=build /app/target/*.jar /app/server.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/server.jar"]