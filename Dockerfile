FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/project-board-1.0.0.jar app.jar
# Render sets PORT at runtime (default 10000); the app reads PORT from the environment.
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
