# syntax=docker/dockerfile:1
# Build context is the repository root, so the shared contracts library is visible:
#   docker build -f services/gateway/Dockerfile .

##### Stage 1: build the application #####
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY packages/common/ packages/common/
COPY services/gateway/pom.xml services/gateway/
COPY services/gateway/src/ services/gateway/src/

# sharing=locked: every service build installs the contracts jar into this shared cache.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -DskipTests -f packages/common/pom.xml install \
    && ./mvnw -B -DskipTests -f services/gateway/pom.xml package \
    && cp services/gateway/target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

##### Stage 2: runtime image #####
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build --chown=spring:spring /build/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /build/extracted/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
