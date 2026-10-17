# ad-service

A gRPC service that serves contextual advertisements for the platform-demo e-commerce platform. It returns ads keyed by product category, falling back to random ads when no context is given or nothing matches. It is stateless, has no downstream dependencies, and is called only by `frontend`.

Part of the [platform-demo](https://github.com/platform-boutique) platform — microservices, observability (LGTM stack), GitOps (Argo CD), policy enforcement (Kyverno), infrastructure provisioning (Crossplane) and an internal developer portal (Backstage). See the master `README.md` in the platform-demo root for the whole-system view.

## At a glance

| | |
|---|---|
| **Language / runtime** | Java 21 (Eclipse Temurin), Gradle, gRPC-Java |
| **Image** | `mladenovskistefan/ad-service` |
| **Protocol / port** | gRPC (HTTP/2, plaintext) on `9555` |
| **Metrics** | Pushed over OTLP/gRPC to Alloy → Mimir every 15 s (no scrape endpoint). Dashboard names: `rpc_server_duration_seconds_{bucket,count,sum}`, `rpc_server_requests_total`, `rpc_server_active_requests` |
| **Health** | Standard `grpc.health.v1.Health/Check` on `9555` (always `SERVING` once the server has started) |
| **State** | None. Ad catalog is hard-coded in `AdsMap.java` |
| **Depends on** | Nothing (optional: OTLP collector, Pyroscope) |
| **Called by** | `frontend` (with a 100 ms client-side deadline) |
| **gRPC reflection** | Not enabled — pass `-proto` to `grpcurl` |

## API

Package `hipstershop`, service `AdService` ([`src/main/proto/ad.proto`](src/main/proto/ad.proto)).

| Method | Request | Response | Behaviour |
|---|---|---|---|
| `GetAds` | `AdRequest{ repeated string context_keys }` | `AdResponse{ repeated Ad ads }` (`Ad{ redirect_url, text }`) | For every context key, returns **all** ads registered for that category. If the result is empty (no keys, or no category matched), returns 2 random ads (may contain the same ad twice). |

Built-in categories: `clothing`, `accessories`, `footwear`, `hair`, `decor`, `kitchen` (two ads). Redirect URLs are relative (`/product/<id>`) and point at products in `product-catalog-db`.

## Configuration

All configuration is via environment variables. Nothing is required; every variable has a default.

| Variable | Default | Description |
|---|---|---|
| `PORT` | `9555` | gRPC listen port |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP/**gRPC** endpoint for **traces, metrics and logs** (scheme required), e.g. `http://alloy.monitoring.svc.cluster.local:4317`. Legacy fallback: `COLLECTOR_SERVICE_ADDR` (`host:port`) |
| `OTEL_METRIC_EXPORT_INTERVAL` | `15000` | Milliseconds between metric pushes |
| `OTEL_SERVICE_NAME` | `ad-service` | `service.name` — becomes the Prometheus `job` and the Loki `service_name`; keep the default, dashboards depend on it |
| `SERVICE_VERSION` | `1.0.0` | `service.version` resource attribute |
| `PYROSCOPE_ADDR` | `http://localhost:4040` | Profiles use the Pyroscope SDK push protocol (**not** OTLP). Point it at Alloy (`http://alloy.monitoring.svc.cluster.local:4040`), which forwards to Pyroscope, or directly at Pyroscope |

Notes:

- Telemetry is **pushed**, not scraped: traces, metrics and logs share one OTLP/gRPC endpoint (Alloy); profiles go to Pyroscope (via Alloy). `service.instance.id` is the pod hostname, so replicas stay distinct in Mimir.
- The former Prometheus endpoint (`METRICS_PORT`) no longer exists. Logs keep going to stdout and are additionally exported over OTLP with trace ids.
- Telemetry and profiling cannot be switched off with a flag; with no reachable collector the exporters just log errors and the service keeps serving.

## Container image

Multi-stage [`Dockerfile`](Dockerfile):

| Stage | Base | What happens |
|---|---|---|
| builder | `eclipse-temurin:21-jdk-alpine` | installs `libc6-compat` (needed by `protoc-gen-grpc-java` on musl), resolves deps, `./gradlew installDist` |
| runtime | `eclipse-temurin:21-jre-alpine` | copies `build/install/hipstershop`, runs as non-root |

- **Entrypoint:** `bin/AdService` (working dir `/app`). Honours `JAVA_OPTS` / `JAVA_TOOL_OPTIONS`.
- **User:** `appuser` — UID `100`, GID `101` (verified on the built image). Not a numeric `USER` in the Dockerfile, so Kubernetes `runAsNonRoot` needs `runAsUser: 100` set explicitly.
- **EXPOSE:** `9555`.
- **Docker `HEALTHCHECK`:** none (the old one hit the removed `/metrics` endpoint). Kubernetes uses gRPC probes (below).
- **Image size:** ~107 MB.
- **Build context:** the service root. No `.dockerignore` is present, so `build/`, `.gradle/` and `.git` are sent to the daemon — add one if builds feel slow.

## Kubernetes deployment notes

Everything a Helm chart for this service needs to know.

**Workload:** stateless `Deployment`. Safe to run multiple replicas (no shared state). Note that gRPC connections from `frontend` are long-lived HTTP/2 and a plain `ClusterIP` Service balances per *connection*, not per request — see "gRPC load balancing" in the master README.

**Ports to declare**

| Name | Port | Protocol | Expose via Service? |
|---|---|---|---|
| `grpc` | `9555` | TCP (HTTP/2) | Yes — `frontend` dials `ad-service:9555` |

**Service DNS name expected by callers:** `ad-service` (`frontend` is configured with `AD_SERVICE_ADDR=ad-service:9555`; use the FQDN `ad-service.<namespace>.svc.cluster.local:9555` if frontend lives in another namespace).

**Probes** (JVM start takes a few seconds)

```yaml
startupProbe:        # generous: JVM + Pyroscope agent init
  grpc: { port: 9555 }
  periodSeconds: 3
  failureThreshold: 30
readinessProbe:
  grpc: { port: 9555 }
  periodSeconds: 5
livenessProbe:
  grpc: { port: 9555 }
  periodSeconds: 10
  failureThreshold: 3
```

`grpc:` probes work because the standard health service is registered (empty service name `""`). 

**Security context** (recommended)

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 100
  runAsGroup: 101
  allowPrivilegeEscalation: false
  capabilities: { drop: ["ALL"] }
  readOnlyRootFilesystem: true   # verified — needs an emptyDir at /tmp that allows exec (Pyroscope native library)
```

**Resources** — requests sized from measured idle usage in the test stack (see the chart's `values.yaml` for limits):

```yaml
resources:
  requests: { cpu: 100m, memory: 192Mi }
  limits:   { cpu: "1", memory: 512Mi }
```

The JVM sizes its heap from the container limit (default 25 %); the chart sets `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError`.

**Config / secrets:** no secrets. All env vars are plain `ConfigMap`/`env` values.

**Telemetry:** pushed, not scraped — OTLP/gRPC to Alloy (`global.monitoring.otlpEndpoint`), profiles to Alloy `:4040`. Metrics carry `job="ad-service"` and `instance=<pod>`; no scrape annotations or `ServiceMonitor` are needed.

**Helm chart:** [`platform-demo-helm/ad-service`](https://github.com/platform-boutique/platform-demo-helm/tree/main/ad-service) (built on the shared `platform-common` library chart). It ships with HA defaults — 2 replicas,
HPA, PodDisruptionBudget, topology spread, NetworkPolicy, restricted pod security context, probes and the full env contract (see Configuration) —
and was verified on a 3-node kind cluster under the `restricted` Pod Security Standard.

**Deployment and admission policies:** Argo CD deploys the chart from `platform-demo-helm` (`envs/<env>/values/ad-service.yaml` pins the image digest; promotion is a pull request). In the cluster Kyverno enforces, for this service: pods named `app.kubernetes.io/name: ad-service` that run under the ServiceAccount of the same name (Cilium derives the network identity from that label), an image from this repository pinned by digest and signed by our release pipeline, a read-only root filesystem, resource requests and a memory limit, readiness and liveness probes and no API token. The Helm CI evaluates the rendered chart against these policies before merge; see [`platform-demo-helm/policies`](https://github.com/platform-boutique/platform-demo-helm/tree/main/policies).

## Observability

| Signal | Implementation | Destination |
|---|---|---|
| **Traces** | OpenTelemetry SDK, OTLP/gRPC span exporter (`BatchSpanProcessor`); inbound spans from the `opentelemetry-grpc-1.6` server interceptor; W3C `traceparent` propagation; parent-based always-on sampling | `OTEL_EXPORTER_OTLP_ENDPOINT` → Alloy → Tempo |
| **Metrics** | OTel SDK instruments in `Metrics.java`, pushed by a `PeriodicMetricReader` over OTLP/gRPC every 15 s (replaces the old Micrometer/Prometheus endpoint) | Alloy → Mimir |
| **Logs** | Log4j2: ECS JSON to stdout **and** the OpenTelemetry appender (OTLP/gRPC, with `trace_id`/`span_id`) | Alloy → Loki |
| **Profiles** | Pyroscope Java agent (`ITIMER`), pushed to `PYROSCOPE_ADDR` | Alloy `:4040` → Pyroscope |

Metric names: `rpc_server_duration_seconds_{bucket,count,sum}` (explicit buckets 1 ms … 10 s), `rpc_server_requests_total`, `rpc_server_active_requests`, labelled `rpc_system`, `rpc_service`, `rpc_method`, `rpc_grpc_status_code`. The gRPC instrumentation library additionally emits `rpc_server_duration_milliseconds_*` (unused by the dashboard).

Every series carries `job="ad-service"` (from `service.name`) and `instance=<pod name>` (`service.instance.id`), which is what the Grafana dashboards select on. Logs land in Loki with `service_name="ad-service"` and trace/span ids, so a log line links to its trace.

The Pyroscope agent extracts a native library into `/tmp` and loads it, so `/tmp` must be writable **and executable** (an `emptyDir` is; a `noexec` tmpfs is not).

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

## Known limitations

- The ad catalog is compiled in; changing ads means a new image.
- `getRandom()` samples with replacement, so the two random ads can be identical.
- Telemetry/profiling are always on (no kill switch); point the endpoints at something reachable or expect periodic exporter error logs.
- Graceful shutdown calls `server.shutdown()` but does not await in-flight RPCs; set a short `terminationGracePeriodSeconds` (10–15 s is plenty).
