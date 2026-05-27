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

# Render provides PORT env var; we fallback to 8080 for local dev.
# Do NOT set SERVER_PORT here as it can override the dynamic PORT on Render.

# Liveness probe: /actuator/health/liveness returns UP as soon as the JVM is
# alive — it does NOT wait for Redis or DB connections to be established.
#
# Previously using /actuator/health caused the container to stay UNHEALTHY for
# 10-30 s on Render cold boots (Redis TLS handshake + Neon serverless wake-up
# added enough latency to fail the first several checks), which in turn delayed
# Render's traffic routing and sometimes triggered "Port not detected" errors.
#
# /actuator/health/liveness is safe here: readiness (Redis + DB + full indicators)
# is still visible at /actuator/health for operator monitoring; this healthcheck
# just gates whether Render should replace the container, not whether it is ready.
# probes.enabled=true is set in application.properties for all profiles.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD sh -c "wget -qO- http://localhost:${PORT:-8080}/actuator/health/liveness \
        | grep -q '\"status\":\"UP\"'" || exit 1

ENTRYPOINT ["/sbin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
