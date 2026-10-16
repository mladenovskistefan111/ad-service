package hipstershop;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.services.HealthStatusManager;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ad Service entrypoint.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Bootstrap observability (traces, metrics, logs over OTLP/gRPC; profiling)
 *   <li>Start the gRPC server with OTel interceptor
 *   <li>Register health status
 * </ul>
 */
public final class AdService {

    private static final Logger logger = LogManager.getLogger(AdService.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        // --- Observability bootstrap ---
        OpenTelemetrySdk otelSdk = Telemetry.init();
        Telemetry.initProfiling();

        // --- gRPC server ---
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "9555"));

        GrpcTelemetry grpcTelemetry = GrpcTelemetry.create(otelSdk);
        Metrics metrics = new Metrics(otelSdk);
        HealthStatusManager healthMgr = new HealthStatusManager();

        Server server = ServerBuilder.forPort(port)
            .addService(new AdServiceImpl(metrics))
            .addService(healthMgr.getHealthService())
            .intercept(grpcTelemetry.newServerInterceptor())
            .build()
            .start();

        healthMgr.setStatus("", ServingStatus.SERVING);
        logger.info("AdService gRPC server started on port {}", port);

        // --- Shutdown hook ---
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down AdService...");
            healthMgr.clearStatus("");
            server.shutdown();
            otelSdk.close();
        }));

        server.awaitTermination();
    }
}
