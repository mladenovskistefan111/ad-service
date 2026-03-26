package hipstershop;

import com.sun.net.httpserver.HttpServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.services.HealthStatusManager;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ad Service entrypoint.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Bootstrap observability (tracing, metrics, profiling)
 *   <li>Start the gRPC server with OTel interceptor
 *   <li>Start the Prometheus metrics HTTP server
 *   <li>Register health status
 * </ul>
 */
public final class AdService {

    private static final Logger logger = LogManager.getLogger(AdService.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        // --- Observability bootstrap ---
        OpenTelemetrySdk otelSdk        = Telemetry.initTracing();
        PrometheusMeterRegistry registry = Telemetry.initMetrics();
        Telemetry.initProfiling();

        // --- gRPC server ---
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "9555"));

        GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(otelSdk);
        Metrics metrics = new Metrics(registry);
        HealthStatusManager healthMgr = new HealthStatusManager();

        Server server = ServerBuilder.forPort(port)
            .addService(new AdServiceImpl(metrics))
            .addService(healthMgr.getHealthService())
            .intercept(grpcTelemetry.newServerInterceptor())
            .build()
            .start();

        healthMgr.setStatus("", ServingStatus.SERVING);
        logger.info("AdService gRPC server started on port {}", port);

        // --- Prometheus metrics HTTP server ---
        int metricsPort = Integer.parseInt(System.getenv().getOrDefault("METRICS_PORT", "9464"));
        startMetricsServer(registry, metricsPort);

        // --- Shutdown hook ---
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down AdService...");
            healthMgr.clearStatus("");
            server.shutdown();
            otelSdk.close();
        }));

        server.awaitTermination();
    }

    private static void startMetricsServer(PrometheusMeterRegistry registry, int port) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/metrics", exchange -> {
            String response = registry.scrape();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        httpServer.start();
        logger.info("Prometheus metrics server listening on :{}/metrics", port);
    }
}