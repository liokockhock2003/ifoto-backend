# Stage 1: Build the JAR
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests
RUN ls -la /app/target/*.jar   # ← confirm JAR exists after build

# Stage 2: Run the JAR
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN ls -la /app/app.jar        # ← confirm JAR was copied correctly
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]