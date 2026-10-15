# Sapio backend (Spring Boot). Build: docker build -t sapio-backend backend/
# Configuration comes from SAPIO_* environment variables (see src/main/resources/application.yml).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package && cp target/sapio-backend-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 sapio && mkdir -p /data/storage && chown -R sapio /data
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER sapio
# Behind Cloud Run / a proxy: https, real client host; uploads land on /data (mount a volume there).
ENV SAPIO_PORT=8080 \
    SAPIO_FORWARD_HEADERS=framework \
    SAPIO_COOKIE_SECURE=true \
    SAPIO_STORAGE_DIR=/data/storage
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
