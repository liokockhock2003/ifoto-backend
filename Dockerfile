# syntax=docker/dockerfile:1

# ── Stage 1: build the jar ────────────────────────────────────────────────────
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached until pom.xml changes
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

# Least-frequently-changed layers first for better image caching
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER spring

# Scheduled jobs (midnight cron) and JDBC serverTimezone assume Malaysia time
ENV TZ=Asia/Kuala_Lumpur
ENV SPRING_PROFILES_ACTIVE=prod
# Size the heap from the container memory limit instead of a fixed -Xmx
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Railway injects PORT; server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
