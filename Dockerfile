# syntax=docker/dockerfile:1
# Kiosk POS API: builds the jar (including code generated from openapi.yaml), then runs
# it on a small JRE image as a non-root user.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml openapi.yaml ./
# Cache dependencies in their own layer so code changes don't re-download them.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package \
    && cp target/kiosk-pos-api-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
EXPOSE 8080
# Size the heap from the container's memory limit; restart rather than limp on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=5s --timeout=3s --start-period=90s --retries=6 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
