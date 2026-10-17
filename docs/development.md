# Develop & test

## Running locally

Requirements: JDK 21, Docker, [`grpcurl`](https://github.com/fullstorydev/grpcurl).

```bash
# build + run
./gradlew installDist
build/install/hipstershop/bin/AdService

# or via Docker
docker build -t ad-service .
docker run --rm -p 9555:9555 \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://host.docker.internal:4317 \
  -e PYROSCOPE_ADDR=http://host.docker.internal:4040 \
  ad-service

# unit tests
./gradlew test
```

### Manual gRPC testing

No reflection, so the proto is required:

```bash
# ads for given categories
grpcurl -plaintext -proto src/main/proto/ad.proto \
  -d '{"context_keys": ["clothing", "kitchen"]}' \
  localhost:9555 hipstershop.AdService/GetAds

# random ads
grpcurl -plaintext -proto src/main/proto/ad.proto -d '{}' \
  localhost:9555 hipstershop.AdService/GetAds

# health (this repo has no health.proto; borrow the one from any sibling service)
grpcurl -plaintext -import-path ../email-service/proto -proto health.proto \
  localhost:9555 grpc.health.v1.Health/Check

# metrics
```

## Project structure

```
├── src/main/
│   ├── java/hipstershop/
│   │   ├── AdService.java       # entrypoint: telemetry bootstrap, gRPC server, metrics HTTP server, shutdown hook
│   │   ├── AdServiceImpl.java   # GetAds handler (+ metrics recording)
│   │   ├── AdsMap.java          # hard-coded ad catalog keyed by category
│   │   ├── Metrics.java         # rpc_server_* Micrometer metrics
│   │   └── Telemetry.java       # OTel tracing + Pyroscope init
│   ├── proto/ad.proto           # service contract
│   └── resources/log4j2.xml     # ECS JSON logging
├── build.gradle                 # gRPC 1.79, OTel 1.49, Micrometer, Pyroscope agent, pinned Netty
├── settings.gradle
├── gradlew / gradle/
└── Dockerfile                   # JDK builder -> JRE runtime
```
