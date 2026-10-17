package hipstershop;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import java.net.InetAddress;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bootstraps all observability concerns:
 * - OpenTelemetry traces, metrics and logs, all pushed over OTLP/gRPC (→ Alloy → Tempo / Mimir / Loki)
 * - Pyroscope continuous profiling (SDK push protocol; PYROSCOPE_ADDR may point at Alloy)
 */
final class Telemetry {

    private static final Logger logger = LogManager.getLogger(Telemetry.class);

    private Telemetry() {}

    /**
     * Initialises the OTel SDK (traces + metrics + logs) and registers it globally. Must be called
     * before the gRPC server starts so that the OTel gRPC interceptor attaches correctly.
     *
     * <p>Endpoint: {@code OTEL_EXPORTER_OTLP_ENDPOINT} (scheme required, e.g. {@code http://alloy:4317});
     * {@code COLLECTOR_SERVICE_ADDR} (host:port) is a legacy fallback.
     */
    static OpenTelemetrySdk init() {
        String serviceName = System.getenv().getOrDefault("OTEL_SERVICE_NAME", "ad-service");
        String serviceVersion = System.getenv().getOrDefault("SERVICE_VERSION", "1.0.0");
        String endpoint = resolveEndpoint();
        long metricIntervalMs = Long.parseLong(System.getenv().getOrDefault("OTEL_METRIC_EXPORT_INTERVAL", "15000"));

        Resource resource = Resource.getDefault().toBuilder()
            .put(AttributeKey.stringKey("service.name"), serviceName)
            .put(AttributeKey.stringKey("service.version"), serviceVersion)
            // Distinguishes replicas: in Kubernetes the hostname is the pod name.
            .put("service.instance.id", instanceId())
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(
                OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build()).build())
            .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .setResource(resource)
            .registerMetricReader(PeriodicMetricReader.builder(
                    OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).build())
                .setInterval(Duration.ofMillis(metricIntervalMs))
                .build())
            .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(
                OtlpGrpcLogRecordExporter.builder().setEndpoint(endpoint).build()).build())
            .build();

        OpenTelemetrySdk otelSdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setLoggerProvider(loggerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();

        // Log4j2 records (configured in log4j2.xml via the OpenTelemetry appender) now flow to the SDK.
        OpenTelemetryAppender.install(otelSdk);

        logger.info("OTel traces, metrics and logs initialised → {} (OTLP/gRPC)", endpoint);
        return otelSdk;
    }

    /**
     * Starts Pyroscope continuous profiling (CPU profile).
     */
    static void initProfiling() {
        String pyroscopeAddr = System.getenv().getOrDefault("PYROSCOPE_ADDR", "http://localhost:4040");
        String serviceName   = System.getenv().getOrDefault("OTEL_SERVICE_NAME", "ad-service");

        PyroscopeAgent.start(
            new Config.Builder()
                .setApplicationName(serviceName)
                .setProfilingEvent(EventType.ITIMER)
                .setServerAddress(pyroscopeAddr)
                .build()
        );

        logger.info("Pyroscope profiling initialised → {}", pyroscopeAddr);
    }

    private static String resolveEndpoint() {
        String endpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
        if (endpoint != null && !endpoint.isBlank()) {
            return endpoint;
        }
        String legacy = System.getenv("COLLECTOR_SERVICE_ADDR");
        if (legacy != null && !legacy.isBlank()) {
            return "http://" + legacy;
        }
        return "http://localhost:4317";
    }

    private static String instanceId() {
        String host = System.getenv("HOSTNAME");
        if (host != null && !host.isBlank()) {
            return host;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
