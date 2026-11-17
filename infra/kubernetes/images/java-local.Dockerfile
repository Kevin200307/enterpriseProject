# Local fast path: build-host-java-images.ps1 freshly builds and extracts these artifacts.
# CI/release publishing continues to use the service-specific multi-stage Dockerfiles.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --chown=spring:spring application/ ./
COPY --chown=spring:spring migration-runner/ ./migration-runner/
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
