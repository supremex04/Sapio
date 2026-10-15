FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 sapio
COPY --from=build /workspace/target/sapio-backend-*.jar /app/app.jar
USER sapio
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
