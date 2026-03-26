# ad-service

A gRPC service that serves contextual advertisements for the platform-demo e-commerce platform. It returns ads based on product category context keys, falling back to random ads when no context is provided. Part of a broader microservices platform built with full observability, GitOps, and internal developer platform tooling.

## Overview

The service exposes one gRPC method:

| Method | Description |
|---|---|
| `GetAds` | Returns up to 2 ads matching the given context keys (product categories), or random ads if no context is provided |

**Port:** `9555` (gRPC)  
**Metrics Port:** `9464` (Prometheus)  
**Protocol:** gRPC  
**Language:** Java 21  
**Called by:** `frontend`

## Requirements

- Java 21+
- Docker
- `grpcurl` for manual testing

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `PORT` | No | gRPC server port (default: `9555`) |
| `METRICS_PORT` | No | Prometheus metrics port (default: `9464`) |
| `OTEL_SERVICE_NAME` | No | Service name reported to OTel (default: `ad-service`) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | OTLP HTTP endpoint (default: `http://localhost:4318`) |
| `PYROSCOPE_ADDR` | No | Pyroscope profiling endpoint (default: `http://localhost:4040`) |
| `SERVICE_VERSION` | No | Service version tag (default: `1.0.0`) |

## Running Locally

### 1. Build and run

```bash
./gradlew installDist
build/install/hipstershop/bin/AdService
```

### 2. Run with Docker

```bash
docker build -t ad-service .

docker run -p 9555:9555 -p 9464:9464 \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4318 \
  -e PYROSCOPE_ADDR=http://pyroscope:4040 \
  ad-service
```

## Testing

### Manual gRPC testing

Install `grpcurl` then, from the service root:

```bash
# get ads with context (returns category-matched ads)
grpcurl -plaintext \
  -proto src/main/proto/ad.proto \
  -d '{"context_keys": ["clothing", "kitchen"]}' \
  localhost:9555 \
  hipstershop.AdService/GetAds

# get random ads (no context)
grpcurl -plaintext \
  -proto src/main/proto/ad.proto \
  -d '{}' \
  localhost:9555 \
  hipstershop.AdService/GetAds

# health check
grpcurl -plaintext \
  -proto src/main/proto/ad.proto \
  localhost:9555 \
  grpc.health.v1.Health/Check
```

### Generate traffic

```bash
while true; do
  grpcurl -plaintext \
    -proto src/main/proto/ad.proto \
    -d '{"context_keys": ["clothing", "kitchen"]}' \
    localhost:9555 \
    hipstershop.AdService/GetAds
  sleep 1
done
```

## Project Structure

```
├── src/main/
│   ├── java/hipstershop/
│   │   ├── AdService.java        # Entrypoint — bootstrap, gRPC server, metrics HTTP server
│   │   ├── AdServiceImpl.java    # gRPC handler implementation
│   │   ├── AdsMap.java           # Static ad catalog keyed by product category
│   │   ├── Metrics.java          # Prometheus metrics (rpc_server_*)
│   │   └── Telemetry.java        # OTel tracing + Pyroscope profiling init
│   ├── proto/
│   │   └── ad.proto              # Self-contained service and message definitions
│   └── resources/
│       └── log4j2.xml            # Structured JSON logging config
├── build.gradle                  # Dependencies — gRPC, OTel, Micrometer, Pyroscope
├── settings.gradle
├── Dockerfile                    # Two-stage build: JDK builder → JRE runtime
└── gradlew
```

## Observability

- **Traces** — OTLP HTTP → Alloy → Tempo. Inbound server spans instrumented via `GrpcTelemetry` interceptor from `opentelemetry-grpc-1.6`.
- **Metrics** — Prometheus endpoint on `:9464/metrics`, scraped by Alloy → Mimir. Exposes `rpc_server_duration`, `rpc_server_requests_total`, `rpc_server_active_requests` matching the OTel RPC semantic conventions used across all platform services.
- **Logs** — Structured JSON logs via Log4j2 to stdout, collected by Alloy via Docker socket → Loki.
- **Profiles** — Continuous CPU profiling via Pyroscope Java SDK → Pyroscope.

## Part Of

This service is part of [platform-demo](https://github.com/mladenovskistefan111) — a full platform engineering project featuring microservices, observability (LGTM stack), GitOps (Argo CD), policy enforcement (Kyverno), infrastructure provisioning (Crossplane), and an internal developer portal (Backstage).