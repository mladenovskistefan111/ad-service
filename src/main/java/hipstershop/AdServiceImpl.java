package hipstershop;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * gRPC service implementation for the Ad Service.
 * Extracted from AdService to follow single-responsibility principle.
 */
final class AdServiceImpl extends AdServiceGrpc.AdServiceImplBase {

    private static final Logger logger = LogManager.getLogger(AdServiceImpl.class);

    private final Metrics metrics;

    AdServiceImpl(Metrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void getAds(AdRequest req, StreamObserver<AdResponse> responseObserver) {
        metrics.incrementActive("GetAds");
        long startNanos = System.nanoTime();
        String statusCode = "0"; // OK

        try {
            logger.info("Received ad request (context_keys={})", req.getContextKeysList());

            List<Ad> ads = new ArrayList<>();

            if (req.getContextKeysCount() > 0) {
                for (String key : req.getContextKeysList()) {
                    Collection<Ad> byCategory = AdsMap.getByCategory(key);
                    ads.addAll(byCategory);
                }
            }

            if (ads.isEmpty()) {
                ads = AdsMap.getRandom();
            }

            AdResponse response = AdResponse.newBuilder().addAllAds(ads).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info("Served {} ads for context_keys={}", ads.size(), req.getContextKeysList());

        } catch (StatusRuntimeException e) {
            logger.warn("GetAds failed with status {}", e.getStatus());
            statusCode = String.valueOf(e.getStatus().getCode().value());
            responseObserver.onError(e);
        } catch (Exception e) {
            logger.error("GetAds failed with unexpected error", e);
            statusCode = "13"; // INTERNAL
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Unexpected error serving ads")
                    .withCause(e)
                    .asRuntimeException()
            );
        } finally {
            double elapsedSeconds = (System.nanoTime() - startNanos) / 1e9;
            metrics.recordCall("GetAds", statusCode, elapsedSeconds);
        }
    }
}