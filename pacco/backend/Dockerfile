# Сборка
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

# Запуск
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 pacco
COPY --from=build /src/target/pacco-backend.jar app.jar
USER pacco
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
