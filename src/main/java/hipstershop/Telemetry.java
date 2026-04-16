package hipstershop;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.EventType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;

/**
 * Bootstraps all observability concerns:
 * - OpenTelemetry tracing (OTLP HTTP → Alloy → Tempo)
 * - Prometheus metrics registry (scraped by Alloy → Mimir)
 * - Pyroscope continuous profiling
 */
final class Telemetry {

    private static final Logger logger = LogManager.getLogger(Telemetry.class);

    private Telemetry() {}

    /**
     * Initialises OTel tracing and returns it. Must be called before the gRPC server starts
     * so that the OTel gRPC interceptor can attach to spans correctly.
     */
    static OpenTelemetrySdk initTracing() {
        String serviceName = System.getenv().getOrDefault("OTEL_SERVICE_NAME", "ad-service");
        String serviceVersion = System.getenv().getOrDefault("SERVICE_VERSION", "1.0.0");
        String otlpEndpoint = System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318");

        Resource resource = Resource.getDefault().toBuilder()
            .put(ResourceAttributes.SERVICE_NAME, serviceName)
            .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
            .build();

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(otlpEndpoint + "/v1/traces")
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .build();

        OpenTelemetrySdk otelSdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
            

        logger.info("OTel tracing initialised → {}/v1/traces", otlpEndpoint);
        return otelSdk;
    }

    /**
     * Creates and returns a Prometheus meter registry. The caller is responsible
     * for starting the HTTP scrape endpoint.
     */
    static PrometheusMeterRegistry initMetrics() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    /**
     * Starts Pyroscope continuous profiling (CPU + allocation profiles).
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
}