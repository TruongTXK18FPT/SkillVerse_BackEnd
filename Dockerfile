# Runtime-only image for Spring Boot backend
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

# Copy the pre-built JAR from build context (must be built before docker build)
ARG JAR_FILE=target/*.jar
COPY --chown=appuser:appgroup ${JAR_FILE} app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

# Run the application with timezone and docker profile set
ENTRYPOINT ["java", "-Duser.timezone=Asia/Ho_Chi_Minh", "-Dspring.profiles.active=docker", "-jar", "app.jar"]