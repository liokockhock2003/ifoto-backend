# Stage 1 — build with Maven
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
# Copy source
COPY src ./src
# Build (skip tests on image build if you choose)
RUN mvn -B clean package -DskipTests

# Stage 2 — runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built jar from the builder stage (adjust artifact name if different)
COPY --from=builder /app/target/*-SNAPSHOT.jar app.jar

# Use platform PORT env var default to 8080
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]