FROM maven:3.9.8-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .

# This step is to cache dependencies, avoiding re-downloading them every time
RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21

WORKDIR /app

COPY --from=build /app/target/*.jar /app/server.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/server.jar"]