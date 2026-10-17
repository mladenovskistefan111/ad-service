# Kubernetes

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
