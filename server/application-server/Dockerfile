FROM maven:3.9.12-eclipse-temurin-21@sha256:7d8cf78b43f2733e53bcf19fbe8c57d9831c7b303dfb72ee3b60a3b9fda6c251 AS build

WORKDIR /app

COPY pom.xml .

# This step is to cache dependencies, avoiding re-downloading them every time
RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21@sha256:e8d57b6c5c73d93212a4384f588309b44bc75fd15b38f78e6a6db1b350dc4ef3

WORKDIR /app

COPY --from=build /app/target/*.jar /app/server.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/server.jar"]