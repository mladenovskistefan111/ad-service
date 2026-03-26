package hipstershop;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom gRPC server metrics matching the OTel RPC semantic conventions
 * used across the platform (rpc_server_duration_seconds, rpc_server_requests_total,
 * rpc_server_active_requests) so all Grafana dashboards work consistently.
 */
final class Metrics {

    private static final String RPC_SYSTEM  = "grpc";
    private static final String RPC_SERVICE = "hipstershop.AdService";

    private final PrometheusMeterRegistry registry;

    private final ConcurrentHashMap<String, AtomicLong> activeRequests = new ConcurrentHashMap<>();

    Metrics(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    PrometheusMeterRegistry getRegistry() {
        return registry;
    }

    void incrementActive(String method) {
        activeGauge(method).incrementAndGet();
    }

    void recordCall(String method, String statusCode, double elapsedSeconds) {
        Tags tags = Tags.of(
            "rpc_system",           RPC_SYSTEM,
            "rpc_service",          RPC_SERVICE,
            "rpc_method",           method,
            "rpc_grpc_status_code", statusCode
        );

        // Timer produces _bucket, _count, _sum — exactly what the dashboard queries
        Timer.builder("rpc_server_duration")
            .description("Duration of inbound gRPC calls")
            .tags(tags)
            .serviceLevelObjectives(
                Duration.ofMillis(1),
                Duration.ofMillis(5),
                Duration.ofMillis(10),
                Duration.ofMillis(25),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofMillis(2500),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
            )
            .register(registry)
            .record((long) (elapsedSeconds * 1_000_000_000L), TimeUnit.NANOSECONDS);

        // Decrement active requests
        activeGauge(method).decrementAndGet();
    }

    private AtomicLong activeGauge(String method) {
        return activeRequests.computeIfAbsent(method, m -> {
            AtomicLong gauge = new AtomicLong(0);
            io.micrometer.core.instrument.Gauge.builder("rpc_server_active_requests", gauge, AtomicLong::get)
                .description("Number of in-flight gRPC calls")
                .tags(
                    "rpc_system",  RPC_SYSTEM,
                    "rpc_service", RPC_SERVICE,
                    "rpc_method",  m
                )
                .register(registry);
            return gauge;
        });
    }
}