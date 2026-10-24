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

# Pick up Alpine security fixes published after the base image was built (the image scan fails on fixable HIGH/CRITICAL findings)
RUN apk upgrade --no-cache

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /app/build/install/hipstershop ./

USER appuser

EXPOSE 9555

ENTRYPOINT ["bin/AdService"]