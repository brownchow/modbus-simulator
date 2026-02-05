# Multi-stage build for Modbus Simulator
# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Configure Maven mirror for China mainland
COPY maven-settings.xml /root/.m2/settings.xml

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S simulator && adduser -S simulator -G simulator

# Copy built JAR from builder stage
COPY --from=builder /app/target/*jar-with-dependencies.jar simulator.jar

# Change ownership
RUN chown -R simulator:simulator /app

# Switch to non-root user
USER simulator:simulator

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD pgrep -f java || exit 1

# Run the application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "simulator.jar"]
