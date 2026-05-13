# =============================================================================
# FinPlay backend — multi-stage, layer-cached, non-root, with healthcheck
# Portable: builds identically on Mac (amd64/arm64) and Windows (amd64).
# =============================================================================

# ---------- 1. Build stage --------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the POM first so dependency resolution is cached.
# Code changes won't bust this layer — only POM changes will.
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

# Now copy source and build.
COPY src src
RUN mvn -B -q -DskipTests clean package \
    && mv target/*.jar /workspace/app.jar

# ---------- 2. Runtime stage ------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# wget for HEALTHCHECK; tini as PID 1 (clean signals).
RUN apk add --no-cache wget tini \
    && addgroup -S finplay \
    && adduser  -S finplay -G finplay

WORKDIR /app
COPY --from=build --chown=finplay:finplay /workspace/app.jar /app/app.jar

USER finplay

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV SERVER_PORT=8080

EXPOSE 8080

# Spring Boot Actuator exposes /actuator/health
# (configured in application.properties + application-prod.properties).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:${SERVER_PORT}/actuator/health \
        | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["/sbin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
