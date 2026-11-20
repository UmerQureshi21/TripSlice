# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
# Local secrets must never end up inside the jar
RUN rm -f src/main/resources/application-secrets.properties && mvn -q clean package -DskipTests

# ---- run stage ----
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
# Don't run as root
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER app
# Leave headroom in the container for FFmpeg child processes
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
