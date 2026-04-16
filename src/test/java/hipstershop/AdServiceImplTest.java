package hipstershop;

import static org.junit.jupiter.api.Assertions.*;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdServiceImplTest {

    private Server server;
    private ManagedChannel channel;
    private AdServiceGrpc.AdServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Metrics metrics = new Metrics(registry);

        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new AdServiceImpl(metrics))
                .directExecutor()
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = AdServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void getAds_withKnownCategory_returnsAds() {
        AdRequest request = AdRequest.newBuilder().addContextKeys("clothing").build();
        AdResponse response = stub.getAds(request);
        assertFalse(response.getAdsList().isEmpty(), "should return ads for known category");
    }

    @Test
    void getAds_withNoContextKeys_returnsRandomAds() {
        AdRequest request = AdRequest.newBuilder().build();
        AdResponse response = stub.getAds(request);
        assertEquals(2, response.getAdsCount(), "empty context should fall back to 2 random ads");
    }

    @Test
    void getAds_withUnknownCategory_fallsBackToRandom() {
        AdRequest request = AdRequest.newBuilder().addContextKeys("unknown-category").build();
        AdResponse response = stub.getAds(request);
        assertEquals(2, response.getAdsCount(), "unknown category should fall back to 2 random ads");
    }

    @Test
    void getAds_responseAdsHaveNonBlankFields() {
        AdRequest request = AdRequest.newBuilder().addContextKeys("footwear").build();
        AdResponse response = stub.getAds(request);
        for (Ad ad : response.getAdsList()) {
            assertFalse(ad.getRedirectUrl().isBlank());
            assertFalse(ad.getText().isBlank());
        }
    }
}
