FROM maven:3.9.12-eclipse-temurin-21@sha256:5ab44df1a8d34911d649713c1f451efc0b7ddea3305f09eb7070fdbb188c51ba AS build

WORKDIR /app

COPY pom.xml .

# This step is to cache dependencies, avoiding re-downloading them every time
RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21@sha256:cd772abe6bc42ddc2f5927756ea33fb26470726438fe0631472cccd4c5ecc304

WORKDIR /app

COPY --from=build /app/target/*.jar /app/server.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/server.jar"]