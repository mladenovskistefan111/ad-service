# Observability

| Signal | Implementation | Destination |
|---|---|---|
| **Traces** | OpenTelemetry SDK, OTLP/gRPC span exporter (`BatchSpanProcessor`); inbound spans from the `opentelemetry-grpc-1.6` server interceptor; W3C `traceparent` propagation; parent-based always-on sampling | `OTEL_EXPORTER_OTLP_ENDPOINT` → Alloy → Tempo |
| **Metrics** | OTel SDK instruments in `Metrics.java`, pushed by a `PeriodicMetricReader` over OTLP/gRPC every 15 s (replaces the old Micrometer/Prometheus endpoint) | Alloy → Mimir |
| **Logs** | Log4j2: ECS JSON to stdout **and** the OpenTelemetry appender (OTLP/gRPC, with `trace_id`/`span_id`) | Alloy → Loki |
| **Profiles** | Pyroscope Java agent (`ITIMER`), pushed to `PYROSCOPE_ADDR` | Alloy `:4040` → Pyroscope |

Metric names: `rpc_server_duration_seconds_{bucket,count,sum}` (explicit buckets 1 ms … 10 s), `rpc_server_requests_total`, `rpc_server_active_requests`, labelled `rpc_system`, `rpc_service`, `rpc_method`, `rpc_grpc_status_code`. The gRPC instrumentation library additionally emits `rpc_server_duration_milliseconds_*` (unused by the dashboard).

Every series carries `job="ad-service"` (from `service.name`) and `instance=<pod name>` (`service.instance.id`), which is what the Grafana dashboards select on. Logs land in Loki with `service_name="ad-service"` and trace/span ids, so a log line links to its trace.

The Pyroscope agent extracts a native library into `/tmp` and loads it, so `/tmp` must be writable **and executable** (an `emptyDir` is; a `noexec` tmpfs is not).
