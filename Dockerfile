# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# libc6-compat needed for protoc-gen-grpc-java binary on Alpine (musl)
RUN apk add --no-cache libc6-compat

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew installDist --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /app/build/install/hipstershop ./

USER appuser

EXPOSE 9555
EXPOSE 9464

HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -qO- http://localhost:${METRICS_PORT:-9464}/metrics > /dev/null || exit 1

ENTRYPOINT ["bin/AdService"]