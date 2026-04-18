# Multi-stage build for Spring Boot backend
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml and Maven configuration first for dependency caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./

# Make mvnw executable
RUN chmod +x mvnw

# Copy source code early (dependency download happens during build)
# This avoids separate dependency:go-offline which can fail with Maven Central issues
COPY src ./src

# Build the application with retry logic for dependency downloads
# Maven config in .mvn/maven.config handles retry settings
RUN ./mvnw clean package -DskipTests -B || \
    (echo "First build attempt failed, retrying in 5 seconds..." && sleep 5 && ./mvnw clean package -DskipTests -B) || \
    (echo "Second build attempt failed, retrying in 10 seconds with fresh local repo..." && sleep 10 && rm -rf /root/.m2/repository && ./mvnw clean package -DskipTests -B)

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

# Install runtime dependencies for health check
RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    tzdata \
    curl \
  && ln -snf /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime \
  && echo "Asia/Ho_Chi_Minh" > /etc/timezone \
  && rm -rf /var/lib/apt/lists/*

# Create app user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup --create-home appuser

# Set working directory
WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/Skillverse_Backend-0.0.1-SNAPSHOT.jar app.jar

# Change ownership of the app directory
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

# Run the application with timezone set
ENTRYPOINT ["java", "-Duser.timezone=Asia/Ho_Chi_Minh", "-jar", "-Dspring.profiles.active=docker", "app.jar"]