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
# /data/media — том с фото и видео товаров; владелец pacco, чтобы новый том унаследовал права
RUN useradd --system --uid 10001 pacco && mkdir -p /data/media && chown pacco /data/media
COPY --from=build /src/target/pacco-backend.jar app.jar
USER pacco
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
