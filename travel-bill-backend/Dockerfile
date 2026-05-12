FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
ENV SERVER_PORT=24975
COPY --from=build /app/target/*.jar app.jar

EXPOSE 24975
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
