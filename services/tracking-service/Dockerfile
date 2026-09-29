# syntax=docker/dockerfile:1

##### Stage 1: build the application #####
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests package \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination target/extracted

##### Stage 2: runtime image #####
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build --chown=spring:spring /build/target/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /build/target/extracted/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
